package com.hotsearch.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MatchEventResponse(
        Long id,
        Long subscriptionId,
        String subscriptionName,
        String keyword,
        LocalDateTime firstSeenAt,
        LocalDateTime lastSeenAt,
        Integer observedCount,
        Integer firstRank,
        Integer bestRank,
        Integer latestRank,
        Long maxHotValue,
        Long latestHotValue,
        String latestLabel,
        String deliveryStatus,
        String notifyReason,
        LocalDateTime notifiedAt,
        String deliveryError,
        boolean active
) {
    public record Summary(long todayNew, long activeCount, long total, List<MatchEventResponse> items) {}
}
