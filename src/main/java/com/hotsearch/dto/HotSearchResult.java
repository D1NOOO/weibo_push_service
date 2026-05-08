package com.hotsearch.dto;

import java.time.LocalDateTime;
import java.util.List;

public record HotSearchResult(
    List<HotSearchItem> items,
    LocalDateTime fetchedAt
) {}
