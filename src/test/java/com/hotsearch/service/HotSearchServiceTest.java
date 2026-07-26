package com.hotsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final HotSearchService service = new HotSearchService(fetcher, repository, new ObjectMapper());

    @Test
    void latestTreatsStoredSnapshotTimeAsUtc() {
        HotSearchSnapshot snapshot = snapshot("2026-07-14T14:40:00", "[]");
        when(repository.findTopByOrderByFetchedAtDesc()).thenReturn(Optional.of(snapshot));

        assertThat(service.getLatestWithMeta().fetchedAt())
                .isEqualTo(Instant.parse("2026-07-14T14:40:00Z"));
    }

    @Test
    void historyAndTrendExposeStoredSnapshotTimeAsUtc() {
        HotSearchSnapshot snapshot = snapshot("2026-07-14T14:40:00", """
                [{"rank":1,"keyword":"测试热搜","label":"热","hotValue":99999,"isAd":false,"url":"https://example.com"}]
                """);
        when(repository.findByFetchedAtAfterOrderByFetchedAtDesc(any(LocalDateTime.class)))
                .thenReturn(List.of(snapshot));

        assertThat(service.getHistorySnapshots(24))
                .singleElement()
                .extracting(entry -> entry.get("fetchedAt"))
                .isEqualTo("2026-07-14T14:40:00Z");
        assertThat(service.getKeywordTrend("测试", 24))
                .singleElement()
                .extracting(entry -> entry.get("fetchedAt"))
                .isEqualTo("2026-07-14T14:40:00Z");
    }

    private HotSearchSnapshot snapshot(String fetchedAt, String items) {
        HotSearchSnapshot snapshot = new HotSearchSnapshot();
        snapshot.setFetchedAt(LocalDateTime.parse(fetchedAt));
        snapshot.setItems(items);
        return snapshot;
    }
}
