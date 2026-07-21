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
    private final ApplicationConfigService configService;
    private final SinkShortLinkService sinkShortLinkService;

    public int getDedupeWindowHours() { return configService.getDedupeWindowHours(); }

    public void setDedupeWindowHours(int hours) {
        configService.setDedupeWindowHours(hours);
    }

    public PipelineService(WeiboFetcher weiboFetcher, HotSearchService hotSearchService,
                           SubscriptionService subscriptionService, ChannelService channelService,
                           SubscriptionMatcher matcher, DeliveryService deliveryService,
                           Map<String, MessageProvider> providerMap,
                           ApplicationConfigService configService,
                           SinkShortLinkService sinkShortLinkService) {
        this.weiboFetcher = weiboFetcher;
        this.hotSearchService = hotSearchService;
        this.subscriptionService = subscriptionService;
        this.channelService = channelService;
        this.matcher = matcher;
        this.deliveryService = deliveryService;
        this.providerMap = providerMap;
        this.configService = configService;
        this.sinkShortLinkService = sinkShortLinkService;
    }

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
        LocalDateTime dedupeSince = LocalDateTime.now().minusHours(configService.getDedupeWindowHours());

        Map<Long, List<MatchResult>> matchesBySub = matches.stream()
                .collect(Collectors.groupingBy(m -> m.subscription().getId(), LinkedHashMap::new, Collectors.toList()));

        for (var entry : matchesBySub.entrySet()) {
            List<MatchResult> subMatches = entry.getValue();
            Subscription sub = subMatches.get(0).subscription();
            if (!sub.isEffectiveAtUtc(LocalDateTime.now(java.time.ZoneOffset.UTC))) continue;

            // Determine which channels to deliver to for this subscription
            List<Long> subChannelIds = sub.getChannelIds();
            for (Channel channel : channels) {
                if (!channel.getUserId().equals(sub.getUserId())) continue;
                // If subscription has specific channels, only send to those
                if (!subChannelIds.isEmpty() && !subChannelIds.contains(channel.getId())) continue;

                MessageProvider provider = providerMap.get(channel.getProvider());
                if (provider == null) {
                    String batchId = UUID.randomUUID().toString();
                    for (MatchResult m : subMatches) {
                        deliveryService.save(buildLog(m, channel, null, "FAILED", "未知的推送提供者: " + channel.getProvider(), batchId));
                    }
                    continue;
                }

                // Get delivery targets (e.g. wechat chats). Default is single empty target.
                List<String> targets = provider.getTargets(channel);

                for (String target : targets) {
                    if (!sub.isEffectiveAtUtc(LocalDateTime.now(java.time.ZoneOffset.UTC))) continue;
                    // Dedupe per target
                    List<MatchResult> toDeliver = new ArrayList<>();
                    for (MatchResult m : subMatches) {
                        if (!deliveryService.isDuplicate(m.item().keyword(), channel.getId(), target, dedupeSince)) {
                            toDeliver.add(m);
                        }
                    }
                    if (toDeliver.isEmpty()) continue;

                    List<HotSearchItem> originalItems = toDeliver.stream().map(MatchResult::item).toList();
                    List<HotSearchItem> allItems = sinkShortLinkService.shortenItems(channel, originalItems);
                    HotSearchItem primaryItem = allItems.get(0);
                    String batchId = UUID.randomUUID().toString();

                    try {
                        provider.send(channel, primaryItem, allItems, target, sub.getName());
                        for (MatchResult m : toDeliver) {
                            deliveryService.save(buildLog(m, channel, target, "SUCCESS", null, batchId));
                        }
                    } catch (Exception e) {
                        log.error("推送失败: subId={}, channel={}, target={}", sub.getId(), channel.getId(), target, e);
                        if (isRateLimited(e)) {
                            if (retryWithBackoff(provider, channel, primaryItem, allItems, target, sub.getName())) {
                                log.info("退避重试成功: keyword={}, channel={}", primaryItem.keyword(), channel.getId());
                                for (MatchResult m : toDeliver) {
                                    deliveryService.save(buildLog(m, channel, target, "SUCCESS", null, batchId));
                                }
                                continue;
                            }
                        }
                        for (MatchResult m : toDeliver) {
                            deliveryService.save(buildLog(m, channel, target, "FAILED", e.getMessage(), batchId));
                        }
                    }
                }
            }
        }
        log.info("管线执行完成");
    }

    private DeliveryLog buildLog(MatchResult match, Channel channel, String target, String status, String error, String batchId) {
        DeliveryLog log = new DeliveryLog();
        log.setSubscriptionId(match.subscription().getId());
        log.setChannelId(channel.getId());
        log.setBatchId(batchId);
        log.setKeyword(match.item().keyword());
        log.setLabel(match.item().label());
        log.setHotValue(match.item().hotValue());
        log.setDeliveredAt(LocalDateTime.now());
        log.setTarget(target != null && !target.isBlank() ? target : null);
        log.setStatus(status);
        log.setError(error);
        return log;
    }

    private static boolean isRateLimited(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("11232") || msg.contains("frequency limited")
                || msg.contains("frequencyLimited") || msg.contains("rate limit"));
    }

    private boolean retryWithBackoff(MessageProvider provider, Channel channel,
                                      HotSearchItem primaryItem, List<HotSearchItem> allItems,
                                      String target, String messageTitle) {
        int maxRetries = 3;
        int delaySeconds = 12;
        for (int i = 0; i < maxRetries; i++) {
            try {
                log.info("退避重试 {}/{}: {}秒后重试 channel={} keyword={}",
                        i + 1, maxRetries, delaySeconds, channel.getId(), primaryItem.keyword());
                Thread.sleep(delaySeconds * 1000L);
                provider.send(channel, primaryItem, allItems, target, messageTitle);
                return true;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception retryEx) {
                if (!isRateLimited(retryEx)) {
                    log.error("重试遇到非限频错误: {}", retryEx.getMessage());
                    return false;
                }
                log.warn("重试 {}/{} 仍然限频", i + 1, maxRetries);
            }
        }
        return false;
    }
}
