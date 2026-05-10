package com.hotsearch.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DeliveryBatchResponse(
    String batchId,
    Long subscriptionId,
    Long channelId,
    String status,
    String error,
    List<KeywordEntry> keywords,
    LocalDateTime deliveredAt
) {
    public record KeywordEntry(String keyword, String label, Long hotValue) {}
}
