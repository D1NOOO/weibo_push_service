package com.hotsearch.service;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import com.hotsearch.entity.DeliveryLog;
import com.hotsearch.entity.Subscription;
import com.hotsearch.fetcher.WeiboFetcher;
import com.hotsearch.matcher.SubscriptionMatcher;
import com.hotsearch.matcher.SubscriptionMatcher.MatchResult;
import com.hotsearch.provider.MessageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final WeiboFetcher weiboFetcher;
    private final HotSearchService hotSearchService;
    private final SubscriptionService subscriptionService;
    private final ChannelService channelService;
    private final SubscriptionMatcher matcher;
    private final DeliveryService deliveryService;
    private final Map<String, MessageProvider> providerMap;

    @Value("${app.dedupe.window-hours:6}")
    private volatile int dedupeWindowHours;

    public int getDedupeWindowHours() { return dedupeWindowHours; }
    public void setDedupeWindowHours(int hours) { this.dedupeWindowHours = hours; }

    public PipelineService(WeiboFetcher weiboFetcher, HotSearchService hotSearchService,
                           SubscriptionService subscriptionService, ChannelService channelService,
                           SubscriptionMatcher matcher, DeliveryService deliveryService,
                           Map<String, MessageProvider> providerMap) {
        this.weiboFetcher = weiboFetcher;
        this.hotSearchService = hotSearchService;
        this.subscriptionService = subscriptionService;
        this.channelService = channelService;
        this.matcher = matcher;
        this.deliveryService = deliveryService;
        this.providerMap = providerMap;
    }

    @Scheduled(cron = "${app.schedule.cron:0 0 * * * *}")
    public void scheduledRun() {
        log.info("定时任务触发：开始执行热搜推送管线");
        runPipeline();
    }

    public void runPipeline() {
        if (!running.compareAndSet(false, true)) {
            log.warn("管线正在执行中，跳过本次触发");
            return;
        }
        try {
            doRunPipeline();
        } finally {
            running.set(false);
        }
    }

    private void doRunPipeline() {
        // 1. Fetch
        List<HotSearchItem> items = hotSearchService.fetchAndSave();
        if (items.isEmpty()) {
            log.info("未获取到热搜数据，跳过");
            return;
        }

        // 2. Match
        List<Subscription> subs = subscriptionService.listAllEnabled();
        List<MatchResult> matches = matcher.match(items, subs);
        if (matches.isEmpty()) {
            log.info("无匹配结果，跳过推送");
            return;
        }

        // 3. Group by subscription, dedupe, then deliver one message per sub-channel
        List<Channel> channels = channelService.listAllEnabled();
        LocalDateTime dedupeSince = LocalDateTime.now().minusHours(dedupeWindowHours);

        Map<Long, List<MatchResult>> matchesBySub = matches.stream()
                .collect(Collectors.groupingBy(m -> m.subscription().getId(), LinkedHashMap::new, Collectors.toList()));

        for (var entry : matchesBySub.entrySet()) {
            List<MatchResult> subMatches = entry.getValue();
            Subscription sub = subMatches.get(0).subscription();

            for (Channel channel : channels) {
                if (!channel.getUserId().equals(sub.getUserId())) continue;

                // Dedupe: filter out already-delivered keywords
                List<MatchResult> toDeliver = new ArrayList<>();
                for (MatchResult m : subMatches) {
                    if (!deliveryService.isDuplicate(m.item().keyword(), channel.getId(), dedupeSince)) {
                        toDeliver.add(m);
                    }
                }
                if (toDeliver.isEmpty()) continue;

                // One message per subscription-channel, with all matched items
                List<HotSearchItem> allItems = toDeliver.stream().map(MatchResult::item).toList();
                HotSearchItem primaryItem = toDeliver.get(0).item();
                String batchId = UUID.randomUUID().toString();

                MessageProvider provider = providerMap.get(channel.getProvider());
                if (provider == null) {
                    for (MatchResult m : toDeliver) {
                        deliveryService.save(buildLog(m, channel, "FAILED", "未知的推送提供者: " + channel.getProvider(), batchId));
                    }
                    continue;
                }

                try {
                    provider.send(channel, primaryItem, allItems);
                    for (MatchResult m : toDeliver) {
                        deliveryService.save(buildLog(m, channel, "SUCCESS", null, batchId));
                    }
                } catch (Exception e) {
                    log.error("推送失败: subId={}, channel={}", sub.getId(), channel.getId(), e);
                    for (MatchResult m : toDeliver) {
                        deliveryService.save(buildLog(m, channel, "FAILED", e.getMessage(), batchId));
                    }
                }
            }
        }
        log.info("管线执行完成");
    }

    private DeliveryLog buildLog(MatchResult match, Channel channel, String status, String error, String batchId) {
        DeliveryLog log = new DeliveryLog();
        log.setSubscriptionId(match.subscription().getId());
        log.setChannelId(channel.getId());
        log.setBatchId(batchId);
        log.setKeyword(match.item().keyword());
        log.setLabel(match.item().label());
        log.setHotValue(match.item().hotValue());
        log.setDeliveredAt(LocalDateTime.now());
        log.setStatus(status);
        log.setError(error);
        return log;
    }
}
