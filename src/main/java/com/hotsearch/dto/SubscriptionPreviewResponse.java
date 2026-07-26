package com.hotsearch.dto;

import java.time.Instant;
import java.util.List;

public record SubscriptionPreviewResponse(
        int matchedCount,
        Instant fetchedAt,
        List<HotSearchItem> matched
) {}
