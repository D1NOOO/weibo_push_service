package com.hotsearch.dto;

/** 应用配置响应。sinkToken 永远只返回掩码或空串，不回显明文。 */
public record AppConfigResponse(
        int dedupeWindowHours,
        int fetchIntervalMinutes,
        int snapshotRetentionDays,
        String sinkBaseUrl,
        String sinkToken,
        boolean sinkConfigured
) {
}
