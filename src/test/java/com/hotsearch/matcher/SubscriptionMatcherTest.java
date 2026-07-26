package com.hotsearch.matcher;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Subscription;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionMatcherTest {

    private final SubscriptionMatcher matcher = new SubscriptionMatcher();
    private final HotSearchItem item = new HotSearchItem(1, "测试热搜", "热", 100L, false, "https://example.com");

    @Test
    void expiredAndFutureSubscriptionsDoNotMatch() {
        Subscription expired = subscription();
        expired.setEndAt(LocalDateTime.parse("2000-01-01T00:00:00"));
        Subscription future = subscription();
        future.setStartAt(LocalDateTime.parse("2100-01-01T00:00:00"));

        assertThat(matcher.match(List.of(item), List.of(expired, future))).isEmpty();
    }

    @Test
    void unboundedSubscriptionStillMatches() {
        assertThat(matcher.match(List.of(item), List.of(subscription()))).hasSize(1);
    }

    private Subscription subscription() {
        Subscription subscription = new Subscription();
        subscription.setEnabled(true);
        subscription.setKeywords(List.of("测试"));
        return subscription;
    }
}
