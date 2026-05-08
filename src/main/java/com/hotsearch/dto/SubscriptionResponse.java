package com.hotsearch.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SubscriptionResponse(
    Long id, String name, List<String> keywords, List<String> excludeKeywords,
    List<String> labels, Integer minHotValue, Boolean enabled, LocalDateTime createdAt
) {}
