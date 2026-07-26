package com.hotsearch.service;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import com.hotsearch.entity.Subscription;
import com.hotsearch.matcher.SubscriptionMatcher.MatchResult;
import com.hotsearch.service.DeliveryPlanner.SubscriptionDelivery;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryPlannerTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-07-20T10:00:00");

    private final DeliveryPlanner planner = new DeliveryPlanner();

    @Test
    void deliversOnlyToChannelsOfTheSubscriptionOwner() {
        Subscription sub = subscription(1L, 10L);
        Channel own = channel(100L, 10L);
        Channel foreign = channel(200L, 20L);

        List<SubscriptionDelivery> plan = planner.plan(
                List.of(new MatchResult(sub, item("A"))), List.of(own, foreign), NOW);

        assertThat(plan).singleElement().satisfies(delivery -> {
            assertThat(delivery.channel().getId()).isEqualTo(100L);
            assertThat(delivery.subscription().getId()).isEqualTo(1L);
        });
    }

    @Test
    void respectsSubscriptionChannelWhitelist() {
        Subscription sub = subscription(1L, 10L);
        sub.setChannelIds(List.of(101L));
        Channel allowed = channel(101L, 10L);
        Channel notAllowed = channel(102L, 10L);

        List<SubscriptionDelivery> plan = planner.plan(
                List.of(new MatchResult(sub, item("A"))), List.of(allowed, notAllowed), NOW);

        assertThat(plan).singleElement()
                .extracting(delivery -> delivery.channel().getId())
                .isEqualTo(101L);
    }

    @Test
    void emptyWhitelistMeansAllOwnChannels() {
        Subscription sub = subscription(1L, 10L);
        Channel first = channel(101L, 10L);
        Channel second = channel(102L, 10L);

        List<SubscriptionDelivery> plan = planner.plan(
                List.of(new MatchResult(sub, item("A"))), List.of(first, second), NOW);

        assertThat(plan).extracting(delivery -> delivery.channel().getId())
                .containsExactly(101L, 102L);
    }

    @Test
    void groupsAllMatchesOfOneSubscriptionIntoOneTaskPerChannel() {
        Subscription sub = subscription(1L, 10L);
        List<MatchResult> matches = List.of(
                new MatchResult(sub, item("A")),
                new MatchResult(sub, item("B")));

        List<SubscriptionDelivery> plan = planner.plan(matches, List.of(channel(100L, 10L)), NOW);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).matches()).extracting(m -> m.item().keyword())
                .containsExactly("A", "B");
    }

    @Test
    void skipsSubscriptionOutsideValidityWindow() {
        Subscription expired = subscription(1L, 10L);
        expired.setEndAt(NOW.minusMinutes(1));

        List<SubscriptionDelivery> plan = planner.plan(
                List.of(new MatchResult(expired, item("A"))), List.of(channel(100L, 10L)), NOW);

        assertThat(plan).isEmpty();
    }

    private static Subscription subscription(Long id, Long userId) {
        Subscription sub = new Subscription();
        sub.setId(id);
        sub.setUserId(userId);
        sub.setName("规则" + id);
        return sub;
    }

    private static Channel channel(Long id, Long userId) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setUserId(userId);
        channel.setProvider("wechat");
        return channel;
    }

    private static HotSearchItem item(String keyword) {
        return new HotSearchItem(1, keyword, null, null, false, "");
    }
}
