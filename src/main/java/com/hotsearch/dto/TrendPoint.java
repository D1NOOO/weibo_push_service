package com.hotsearch.dto;

/** 关键词在某一时刻的排名趋势点。fetchedAt 为 ISO-8601 UTC 字符串。 */
public record TrendPoint(String fetchedAt, int rank, Long hotValue, String label) {
}
