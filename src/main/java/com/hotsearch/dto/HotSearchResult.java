package com.hotsearch.dto;

import java.time.Instant;
import java.util.List;

public record HotSearchResult(
    List<HotSearchItem> items,
    Instant fetchedAt
) {}
