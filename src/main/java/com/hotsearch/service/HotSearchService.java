package com.hotsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.dto.HotSearchResult;
import com.hotsearch.entity.HotSearchSnapshot;
import com.hotsearch.fetcher.WeiboFetcher;
import com.hotsearch.repository.HotSearchSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class HotSearchService {

    private final WeiboFetcher weiboFetcher;
    private final HotSearchSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public HotSearchService(WeiboFetcher weiboFetcher, HotSearchSnapshotRepository snapshotRepository, ObjectMapper objectMapper) {
        this.weiboFetcher = weiboFetcher;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    public List<HotSearchItem> fetchAndSave() {
        List<HotSearchItem> items = weiboFetcher.fetch();
        HotSearchSnapshot snapshot = new HotSearchSnapshot();
        snapshot.setFetchedAt(LocalDateTime.now());
        snapshot.setItemsObject(items);
        snapshotRepository.save(snapshot);
        return items;
    }

    public List<HotSearchItem> getLatest() {
        return snapshotRepository.findTopByOrderByFetchedAtDesc()
                .map(s -> parseItems(s.getItems()))
                .orElse(List.of());
    }

    public HotSearchResult getLatestWithMeta() {
        return snapshotRepository.findTopByOrderByFetchedAtDesc()
                .map(s -> new HotSearchResult(parseItems(s.getItems()), s.getFetchedAt()))
                .orElse(new HotSearchResult(List.of(), null));
    }

    /**
     * Get history snapshots summary for the given hours.
     * Returns a list of { fetchedAt, itemCount, topKeywords } maps.
     */
    public List<Map<String, Object>> getHistorySnapshots(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<HotSearchSnapshot> snapshots = snapshotRepository.findByFetchedAtAfterOrderByFetchedAtDesc(since);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (HotSearchSnapshot snap : snapshots) {
            List<HotSearchItem> items = parseItems(snap.getItems());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("fetchedAt", snap.getFetchedAt().toString());
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
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<HotSearchSnapshot> snapshots = snapshotRepository.findByFetchedAtAfterOrderByFetchedAtDesc(since);
        
        List<Map<String, Object>> trend = new ArrayList<>();
        for (HotSearchSnapshot snap : snapshots) {
            List<HotSearchItem> items = parseItems(snap.getItems());
            items.stream()
                    .filter(item -> item.keyword() != null && item.keyword().contains(keyword))
                    .findFirst()
                    .ifPresent(item -> {
                        Map<String, Object> point = new LinkedHashMap<>();
                        point.put("fetchedAt", snap.getFetchedAt().toString());
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
}
