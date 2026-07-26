package com.hotsearch.service;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import com.hotsearch.entity.DeliveryLog;
import com.hotsearch.entity.Subscription;
import com.hotsearch.matcher.SubscriptionMatcher.MatchResult;
import com.hotsearch.provider.MessageProvider;
import com.hotsearch.provider.PushMessage;
import com.hotsearch.provider.RateLimitedException;
import com.hotsearch.service.DeliveryPlanner.SubscriptionDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 执行单个「订阅 × 通道」投递任务：解析目标、按目标去重、短链替换、发送并记录推送日志；
 * 遇上游限频按固定间隔退避重试。
 */
@Component
public class DeliveryExecutor {

    private static final Logger log = LoggerFactory.getLogger(DeliveryExecutor.class);

    private final DeliveryService deliveryService;
    private final SinkShortLinkService sinkShortLinkService;
    private final Map<String, MessageProvider> providerMap;
    private final int retryMaxAttempts;
    private final long retryDelayMillis;

    public DeliveryExecutor(DeliveryService deliveryService,
                            SinkShortLinkService sinkShortLinkService,
                            Map<String, MessageProvider> providerMap,
                            @Value("${app.push.retry.max-attempts:3}") int retryMaxAttempts,
                            @Value("${app.push.retry.delay-seconds:12}") long retryDelaySeconds) {
        this.deliveryService = deliveryService;
        this.sinkShortLinkService = sinkShortLinkService;
        this.providerMap = providerMap;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryDelayMillis = retryDelaySeconds * 1000L;
    }

    public void execute(SubscriptionDelivery delivery, LocalDateTime dedupeSince) {
        Subscription sub = delivery.subscription();
        Channel channel = delivery.channel();

        MessageProvider provider = providerMap.get(channel.getProvider());
        if (provider == null) {
            saveAll(delivery.matches(), sub, channel, null, "FAILED",
                    "未知的推送提供者: " + channel.getProvider());
            return;
        }

        List<String> targets = provider.getTargets(channel);
        if (targets.isEmpty()) {
            log.warn("通道 {} 未配置投递目标，订阅 {} 本轮不投递", channel.getId(), sub.getId());
            saveAll(delivery.matches(), sub, channel, null, "FAILED", "通道未配置目标聊天");
            return;
        }

        for (String target : targets) {
            List<MatchResult> toDeliver = delivery.matches().stream()
                    .filter(m -> !deliveryService.isDuplicate(
                            m.item().keyword(), channel.getId(), target, dedupeSince))
                    .toList();
            if (toDeliver.isEmpty()) continue;

            List<HotSearchItem> items = sinkShortLinkService.shortenItems(channel,
                    toDeliver.stream().map(MatchResult::item).toList());
            PushMessage message = new PushMessage(channel, items, target, sub.getName());

            try {
                sendWithRetry(provider, message);
                saveAll(toDeliver, sub, channel, target, "SUCCESS", null);
            } catch (Exception e) {
                log.error("推送失败: subId={}, channel={}, target={}",
                        sub.getId(), channel.getId(), target, e);
                saveAll(toDeliver, sub, channel, target, "FAILED", e.getMessage());
            }
        }
    }

    /** 限频时按固定间隔重试；非限频异常直接抛出。 */
    private void sendWithRetry(MessageProvider provider, PushMessage message) {
        try {
            provider.send(message);
        } catch (RateLimitedException first) {
            for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
                try {
                    log.info("限频退避重试 {}/{}: {}ms 后重试 keyword={}",
                            attempt, retryMaxAttempts, retryDelayMillis, message.primaryItem().keyword());
                    Thread.sleep(retryDelayMillis);
                    provider.send(message);
                    log.info("退避重试成功: keyword={}", message.primaryItem().keyword());
                    return;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw first;
                } catch (RateLimitedException stillLimited) {
                    log.warn("重试 {}/{} 仍然限频", attempt, retryMaxAttempts);
                }
            }
            throw first;
        }
    }

    private void saveAll(List<MatchResult> matches, Subscription sub, Channel channel,
                         String target, String status, String error) {
        String batchId = UUID.randomUUID().toString();
        for (MatchResult match : matches) {
            DeliveryLog entry = new DeliveryLog();
            entry.setSubscriptionId(sub.getId());
            entry.setChannelId(channel.getId());
            entry.setBatchId(batchId);
            entry.setKeyword(match.item().keyword());
            entry.setLabel(match.item().label());
            entry.setHotValue(match.item().hotValue());
            entry.setDeliveredAt(LocalDateTime.now(ZoneOffset.UTC));
            entry.setTarget(target != null && !target.isBlank() ? target : null);
            entry.setStatus(status);
            entry.setError(error);
            deliveryService.save(entry);
        }
    }
}
