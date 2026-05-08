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
import java.util.List;
import java.util.Map;

@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    private final WeiboFetcher weiboFetcher;
    private final HotSearchService hotSearchService;
    private final SubscriptionService subscriptionService;
    private final ChannelService channelService;
    private final SubscriptionMatcher matcher;
    private final DeliveryService deliveryService;
    private final Map<String, MessageProvider> providerMap;

    @Value("${app.dedupe.window-hours:6}")
    private int dedupeWindowHours;

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

        // 3. Dedupe + Deliver
        List<Channel> channels = channelService.listAllEnabled();
        LocalDateTime dedupeSince = LocalDateTime.now().minusHours(dedupeWindowHours);

        for (MatchResult match : matches) {
            for (Channel channel : channels) {
                if (!channel.getUserId().equals(match.subscription().getUserId())) continue;

                // Dedupe check
                if (deliveryService.isDuplicate(match.item().keyword(), channel.getId(), dedupeSince)) {
                    log.debug("去重跳过: keyword={}, channelId={}", match.item().keyword(), channel.getId());
                    continue;
                }

                // Deliver
                DeliveryLog deliveryLog = new DeliveryLog();
                deliveryLog.setSubscriptionId(match.subscription().getId());
                deliveryLog.setChannelId(channel.getId());
                deliveryLog.setKeyword(match.item().keyword());
                deliveryLog.setLabel(match.item().label());
                deliveryLog.setHotValue(match.item().hotValue());
                deliveryLog.setDeliveredAt(LocalDateTime.now());

                MessageProvider provider = providerMap.get(channel.getProvider());
                if (provider == null) {
                    deliveryLog.setStatus("FAILED");
                    deliveryLog.setError("未知的推送提供者: " + channel.getProvider());
                    deliveryService.save(deliveryLog);
                    continue;
                }

                try {
                    provider.send(channel, match.item(), matches.stream()
                            .filter(m -> m.subscription().getId().equals(match.subscription().getId()))
                            .map(MatchResult::item).toList());
                    deliveryLog.setStatus("SUCCESS");
                } catch (Exception e) {
                    deliveryLog.setStatus("FAILED");
                    deliveryLog.setError(e.getMessage());
                    log.error("推送失败: keyword={}, channel={}", match.item().keyword(), channel.getId(), e);
                }
                deliveryService.save(deliveryLog);
            }
        }
        log.info("管线执行完成");
    }
}
