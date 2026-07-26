package com.hotsearch.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-20T10:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allowsFiveAttemptsThenBlocks() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4")).as("attempt %d", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
        assertThat(limiter.remainingSeconds("1.2.3.4")).isPositive();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 6; i++) limiter.tryAcquire("1.1.1.1");

        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();
    }

    @Test
    void windowExpiryResetsTheCounter() {
        for (int i = 0; i < 6; i++) limiter.tryAcquire("1.2.3.4");

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.remainingSeconds("1.2.3.4")).isZero();
    }

    @Test
    void remainingSecondsCountsDownWithinTheWindow() {
        for (int i = 0; i < 6; i++) limiter.tryAcquire("1.2.3.4");

        clock.advance(Duration.ofSeconds(20));

        assertThat(limiter.remainingSeconds("1.2.3.4")).isEqualTo(40);
    }

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
