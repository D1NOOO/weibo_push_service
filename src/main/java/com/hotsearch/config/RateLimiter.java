package com.hotsearch.config;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 60;
    private static final int CLEANUP_THRESHOLD = 1_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> store = new ConcurrentHashMap<>();

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        purgeIfOversized(now);
        Window window = store.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new Window(now, 1);
            }
            return new Window(existing.windowStart, existing.count + 1);
        });
        return window.count <= MAX_ATTEMPTS;
    }

    public long remainingSeconds(String key) {
        Window window = store.get(key);
        if (window == null || window.count <= MAX_ATTEMPTS) return 0;
        long elapsed = clock.instant().getEpochSecond() - window.windowStart.getEpochSecond();
        return Math.max(0, WINDOW_SECONDS - elapsed);
    }

    /** 限流键随客户端 IP 无限累积，超过阈值时清掉已过期窗口，避免内存缓慢增长。 */
    private void purgeIfOversized(Instant now) {
        if (store.size() < CLEANUP_THRESHOLD) return;
        store.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private record Window(Instant windowStart, int count) {
        boolean isExpired(Instant now) {
            return now.isAfter(windowStart.plusSeconds(WINDOW_SECONDS));
        }
    }
}
