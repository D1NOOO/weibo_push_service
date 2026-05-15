package com.hotsearch.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DeliveryLogEntry(
    String keyword,
    String label,
    Long hotValue,
    List<ChannelDelivery> channels,
    LocalDateTime deliveredAt
) {
    public record ChannelDelivery(
        String provider,
        String target,
        String status,
        String error
    ) {}
}
