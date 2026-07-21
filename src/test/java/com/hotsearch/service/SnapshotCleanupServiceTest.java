package com.hotsearch.service;

import com.hotsearch.repository.HotSearchSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotCleanupServiceTest {

    @Test
    void deletesSnapshotsOlderThanConfiguredRetention() {
        HotSearchSnapshotRepository repository = mock(HotSearchSnapshotRepository.class);
        ApplicationConfigService configService = mock(ApplicationConfigService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-20T08:00:00Z"), ZoneOffset.UTC);
        when(configService.getSnapshotRetentionDays()).thenReturn(30);
        LocalDateTime expectedCutoff = LocalDateTime.parse("2026-06-20T08:00:00");
        when(repository.deleteFetchedBefore(expectedCutoff)).thenReturn(123);
        SnapshotCleanupService service = new SnapshotCleanupService(repository, configService, clock);

        assertThat(service.cleanupExpiredSnapshots()).isEqualTo(123);
        verify(repository).deleteFetchedBefore(expectedCutoff);
    }
}
