package com.hotsearch.service;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.HotSearchSnapshot;
import com.hotsearch.fetcher.WeiboFetcher;
import com.hotsearch.repository.HotSearchSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HotSearchServiceTest {

    private final WeiboFetcher fetcher = mock(WeiboFetcher.class);
    private final HotSearchSnapshotRepository repository = mock(HotSearchSnapshotRepository.class);
    private final HotSearchService service = new HotSearchService(fetcher, repository);

    @Test
    void latestTreatsStoredSnapshotTimeAsUtc() {
        HotSearchSnapshot snapshot = snapshot("2026-07-14T14:40:00", List.of());
        when(repository.findTopByOrderByFetchedAtDesc()).thenReturn(Optional.of(snapshot));

        assertThat(service.getLatestWithMeta().fetchedAt())
                .isEqualTo(Instant.parse("2026-07-14T14:40:00Z"));
    }

    @Test
    void historyAndTrendExposeStoredSnapshotTimeAsUtc() {
        HotSearchSnapshot snapshot = snapshot("2026-07-14T14:40:00", List.of(
                new HotSearchItem(1, "测试热搜", "热", 99999L, false, "https://example.com")));
        when(repository.findByFetchedAtAfterOrderByFetchedAtDesc(any(LocalDateTime.class)))
                .thenReturn(List.of(snapshot));

        assertThat(service.getHistorySnapshots(24))
                .singleElement()
                .extracting(entry -> entry.fetchedAt())
                .isEqualTo("2026-07-14T14:40:00Z");
        assertThat(service.getKeywordTrend("测试", 24))
                .singleElement()
                .extracting(point -> point.fetchedAt())
                .isEqualTo("2026-07-14T14:40:00Z");
    }

    @Test
    void trendMatchesKeywordByContains() {
        HotSearchSnapshot snapshot = snapshot("2026-07-14T14:40:00", List.of(
                new HotSearchItem(1, "无关词条", null, 1L, false, ""),
                new HotSearchItem(2, "世界杯决赛", "爆", 500_000L, false, "")));
        when(repository.findByFetchedAtAfterOrderByFetchedAtDesc(any(LocalDateTime.class)))
                .thenReturn(List.of(snapshot));

        assertThat(service.getKeywordTrend("世界杯", 24))
                .singleElement()
                .satisfies(point -> {
                    assertThat(point.rank()).isEqualTo(2);
                    assertThat(point.hotValue()).isEqualTo(500_000L);
                    assertThat(point.label()).isEqualTo("爆");
                });
    }

    private HotSearchSnapshot snapshot(String fetchedAt, List<HotSearchItem> items) {
        HotSearchSnapshot snapshot = new HotSearchSnapshot();
        snapshot.setFetchedAt(LocalDateTime.parse(fetchedAt));
        snapshot.setItems(items);
        return snapshot;
    }
}
