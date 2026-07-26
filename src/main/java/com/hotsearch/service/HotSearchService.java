package com.hotsearch.service;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.dto.HotSearchResult;
import com.hotsearch.dto.SnapshotSummary;
import com.hotsearch.dto.TrendPoint;
import com.hotsearch.entity.HotSearchSnapshot;
import com.hotsearch.fetcher.WeiboFetcher;
import com.hotsearch.repository.HotSearchSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class HotSearchService {

    private final WeiboFetcher weiboFetcher;
    private final HotSearchSnapshotRepository snapshotRepository;

    public HotSearchService(WeiboFetcher weiboFetcher, HotSearchSnapshotRepository snapshotRepository) {
        this.weiboFetcher = weiboFetcher;
        this.snapshotRepository = snapshotRepository;
    }

    public List<HotSearchItem> fetchAndSave() {
        List<HotSearchItem> items = weiboFetcher.fetch();
        HotSearchSnapshot snapshot = new HotSearchSnapshot();
        snapshot.setFetchedAt(LocalDateTime.now(ZoneOffset.UTC));
        snapshot.setItems(items);
        snapshotRepository.save(snapshot);
        return items;
    }

    public HotSearchResult getLatestWithMeta() {
        return snapshotRepository.findTopByOrderByFetchedAtDesc()
                .map(s -> new HotSearchResult(s.getItems(), toInstant(s.getFetchedAt())))
                .orElse(new HotSearchResult(List.of(), null));
    }

    /** 最近 hours 小时内的快照摘要列表（倒序）。 */
    public List<SnapshotSummary> getHistorySnapshots(int hours) {
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours);
        List<HotSearchSnapshot> snapshots = snapshotRepository.findByFetchedAtAfterOrderByFetchedAtDesc(since);

        List<SnapshotSummary> result = new ArrayList<>(snapshots.size());
        for (HotSearchSnapshot snapshot : snapshots) {
            List<HotSearchItem> items = snapshot.getItems();
            result.add(new SnapshotSummary(
                    toInstant(snapshot.getFetchedAt()).toString(),
                    items.size(),
                    items.stream().limit(3).map(HotSearchItem::keyword).toList()
            ));
        }
        return result;
    }

    /** 关键词（模糊包含）在最近 hours 小时内的排名趋势（倒序）。 */
    public List<TrendPoint> getKeywordTrend(String keyword, int hours) {
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours);
        List<HotSearchSnapshot> snapshots = snapshotRepository.findByFetchedAtAfterOrderByFetchedAtDesc(since);

        List<TrendPoint> trend = new ArrayList<>();
        for (HotSearchSnapshot snapshot : snapshots) {
            snapshot.getItems().stream()
                    .filter(item -> item.keyword() != null && item.keyword().contains(keyword))
                    .findFirst()
                    .ifPresent(item -> trend.add(new TrendPoint(
                            toInstant(snapshot.getFetchedAt()).toString(),
                            item.rank(),
                            item.hotValue(),
                            item.label()
                    )));
        }
        return trend;
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC);
    }
}
