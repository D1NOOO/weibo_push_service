package com.hotsearch.service;

import com.hotsearch.entity.Channel;
import com.hotsearch.entity.Subscription;
import com.hotsearch.matcher.SubscriptionMatcher.MatchResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把匹配结果规划为「订阅 × 通道」粒度的投递任务：
 * 按订阅分组、校验有效期、把通道按用户预索引并应用订阅的通道白名单。
 * 纯计算无副作用，去重与发送由 DeliveryExecutor 负责。
 */
@Component
public class DeliveryPlanner {

    /** 一个订阅在一个通道上的待投递集合。 */
    public record SubscriptionDelivery(Subscription subscription, Channel channel, List<MatchResult> matches) {}

    public List<SubscriptionDelivery> plan(List<MatchResult> matches, List<Channel> channels, LocalDateTime utcNow) {
        Map<Long, List<MatchResult>> matchesBySub = matches.stream()
                .collect(Collectors.groupingBy(m -> m.subscription().getId(),
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<Channel>> channelsByUser = channels.stream()
                .collect(Collectors.groupingBy(Channel::getUserId,
                        LinkedHashMap::new, Collectors.toList()));

        List<SubscriptionDelivery> plan = new ArrayList<>();
        for (List<MatchResult> subMatches : matchesBySub.values()) {
            Subscription sub = subMatches.get(0).subscription();
            if (!sub.isEffectiveAtUtc(utcNow)) continue;

            List<Long> subChannelIds = sub.getChannelIds();
            for (Channel channel : channelsByUser.getOrDefault(sub.getUserId(), List.of())) {
                // 订阅指定了通道白名单时，只投递到白名单内的通道
                if (!subChannelIds.isEmpty() && !subChannelIds.contains(channel.getId())) continue;
                plan.add(new SubscriptionDelivery(sub, channel, subMatches));
            }
        }
        return plan;
    }
}
