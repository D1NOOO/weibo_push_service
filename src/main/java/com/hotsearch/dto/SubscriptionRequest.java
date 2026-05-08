package com.hotsearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SubscriptionRequest(
    @NotBlank String name,
    List<String> keywords,
    List<String> excludeKeywords,
    List<String> labels,
    Integer minHotValue,
    @NotNull Boolean enabled
) {}
