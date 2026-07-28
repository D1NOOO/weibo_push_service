package com.hotsearch.dto;

import java.time.Instant;
import java.util.Map;

public record ChannelResponse(
    Long id, String provider, Map<String, Object> config, Boolean enabled, Instant createdAt
) {}
