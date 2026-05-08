package com.hotsearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ChannelRequest(
    @NotBlank String provider,
    Map<String, Object> config,
    @NotNull Boolean enabled
) {}
