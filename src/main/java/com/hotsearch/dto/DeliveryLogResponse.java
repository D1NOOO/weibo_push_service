package com.hotsearch.dto;

import java.time.LocalDateTime;

public record DeliveryLogResponse(
    Long id, Long subscriptionId, Long channelId, String keyword,
    String label, Long hotValue, String status, String error, LocalDateTime deliveredAt
) {}
