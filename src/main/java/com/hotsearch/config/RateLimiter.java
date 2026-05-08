package com.hotsearch.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 60;

    private final ConcurrentHashMap<String, Window> store = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        Instant now = Instant.now();
        store.compute(key, (k, window) -> {
            if (window == null || now.isAfter(window.windowStart.plusSeconds(WINDOW_SECONDS))) {
                return new Window(now, 1);
            }
            return new Window(window.windowStart, window.count + 1);
        });
        Window w = store.get(key);
        return w != null && w.count <= MAX_ATTEMPTS;
    }

    public long remainingSeconds(String key) {
        Window w = store.get(key);
        if (w == null || w.count <= MAX_ATTEMPTS) return 0;
        long elapsed = Instant.now().getEpochSecond() - w.windowStart.getEpochSecond();
        return Math.max(0, WINDOW_SECONDS - elapsed);
    }

    private record Window(Instant windowStart, int count) {}
}
