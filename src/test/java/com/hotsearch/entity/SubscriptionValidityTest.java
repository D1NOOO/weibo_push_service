package com.hotsearch.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionValidityTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-07-20T10:00:00");

    @Test
    void noValidityBoundsMeansLongTerm() {
        assertThat(new Subscription().isEffectiveAtUtc(NOW)).isTrue();
    }

    @Test
    void startIsInclusiveAndEndIsExclusive() {
        Subscription subscription = new Subscription();
        subscription.setStartAt(NOW);
        subscription.setEndAt(NOW.plusSeconds(10));

        assertThat(subscription.isEffectiveAtUtc(NOW)).isTrue();
        assertThat(subscription.isEffectiveAtUtc(NOW.plusSeconds(9))).isTrue();
        assertThat(subscription.isEffectiveAtUtc(NOW.plusSeconds(10))).isFalse();
    }
}
