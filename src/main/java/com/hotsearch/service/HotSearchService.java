package com.hotsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.dto.HotSearchResult;
import com.hotsearch.entity.HotSearchSnapshot;
import com.hotsearch.fetcher.WeiboFetcher;
import com.hotsearch.repository.HotSearchSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class HotSearchService {

    private final WeiboFetcher weiboFetcher;
    private final HotSearchSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public HotSearchService(WeiboFetcher weiboFetcher, HotSearchSnapshotRepository snapshotRepository,
                            ObjectMapper objectMapper) {
        this.weiboFetcher = weiboFetcher;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    // 抓取状态（进程内，重启后由最新快照兜底）
    private volatile Instant lastAttemptAt;
    private volatile Instant lastSuccessAt;
    private volatile int lastItemCount;

    public List<HotSearchItem> fetchAndSave() {
        lastAttemptAt = Instant.now();
        List<HotSearchItem> items = weiboFetcher.fetch();
        lastItemCount = items.size();
        if (!items.isEmpty()) {
            lastSuccessAt = Instant.now();
        }
        HotSearchSnapshot snapshot = new HotSearchSnapshot();
        snapshot.setFetchedAt(LocalDateTime.now(ZoneOffset.UTC));
        snapshot.setItemsObject(items);
        snapshotRepository.save(snapshot);
        return items;
    }

    /** 抓取状态：最近尝试/成功时间、条数（进程内数据缺失时回落到最新快照）。 */
    public Map<String, Object> getFetchStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        Instant snapshotAt = snapshotRepository.findTopByOrderByFetchedAtDesc()
                .map(s -> toInstant(s.getFetchedAt()))
                .orElse(null);
        Instant attemptAt = lastAttemptAt != null ? lastAttemptAt : snapshotAt;
        Instant successAt = lastSuccessAt != null ? lastSuccessAt : snapshotAt;
        int itemCount = lastAttemptAt != null ? lastItemCount
                : snapshotRepository.findTopByOrderByFetchedAtDesc()
                        .map(s -> parseItems(s.getItems()).size()).orElse(0);
        status.put("lastAttemptAt", attemptAt);
        status.put("lastSuccessAt", successAt);
        status.put("lastItemCount", itemCount);
        status.put("healthy", itemCount > 0);
        return status;
    }

    public List<HotSearchItem> getLatest() {
        return snapshotRepository.findTopByOrderByFetchedAtDesc()
                .map(s -> parseItems(s.getItems()))
                .orElse(List.of());
    }

    public HotSearchResult getLatestWithMeta() {
        return snapshotRepository.findTopByOrderByFetchedAtDesc()
                .map(s -> new HotSearchResult(parseItems(s.getItems()),
                        toInstant(s.getFetchedAt())))
                .orElse(new HotSearchResult(List.of(), null));
    }

    /**
     * Get history snapshots summary for the given hours.
     * Returns a list of { fetchedAt, itemCount, topKeywords } maps.
     */
    public List<Map<String, Object>> getHistorySnapshots(int hours) {
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours);
        List<HotSearchSnapshot> snapshots = snapshotRepository.findByFetchedAtAfterOrderByFetchedAtDesc(since);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (HotSearchSnapshot snap : snapshots) {
            List<HotSearchItem> items = parseItems(snap.getItems());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("fetchedAt", toInstant(snap.getFetchedAt()).toString());
            entry.put("itemCount", items.size());
            // Top 3 keywords as summary
            entry.put("topKeywords", items.stream().limit(3)
                    .map(HotSearchItem::keyword).toList());
            result.add(entry);
        }
        return result;
    }

    /**
     * Get rank trend for a specific keyword over time.
     * Returns [{ fetchedAt, rank, hotValue, label }]
     */
    public List<Map<String, Object>> getKeywordTrend(String keyword, int hours) {
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours);
        List<HotSearchSnapshot> snapshots = snapshotRepository.findByFetchedAtAfterOrderByFetchedAtDesc(since);
        
        List<Map<String, Object>> trend = new ArrayList<>();
        for (HotSearchSnapshot snap : snapshots) {
            List<HotSearchItem> items = parseItems(snap.getItems());
            items.stream()
                    .filter(item -> item.keyword() != null && item.keyword().contains(keyword))
                    .findFirst()
                    .ifPresent(item -> {
                        Map<String, Object> point = new LinkedHashMap<>();
                        point.put("fetchedAt", toInstant(snap.getFetchedAt()).toString());
                        point.put("rank", item.rank());
                        point.put("hotValue", item.hotValue());
                        point.put("label", item.label());
                        trend.add(point);
                    });
        }
        return trend;
    }

    private List<HotSearchItem> parseItems(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, HotSearchItem.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC);
    }
}
