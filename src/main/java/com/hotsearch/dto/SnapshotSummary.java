package com.hotsearch.dto;

import java.util.List;

/** 一次热搜快照的摘要（历史列表项）。fetchedAt 为 ISO-8601 UTC 字符串。 */
public record SnapshotSummary(String fetchedAt, int itemCount, List<String> topKeywords) {
}
