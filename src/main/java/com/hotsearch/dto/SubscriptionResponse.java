package com.hotsearch.dto;

import java.time.Instant;
import java.util.List;

public record SubscriptionResponse(
    Long id, String name, List<String> keywords, List<String> excludeKeywords,
    List<String> labels, Integer minHotValue, List<Long> channelIds, Boolean enabled,
    Instant createdAt, Instant startAt, Instant endAt
) {}
