package com.hotsearch.service;

import com.hotsearch.repository.DeliveryLogRepository;
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

    private final HotSearchSnapshotRepository snapshotRepository = mock(HotSearchSnapshotRepository.class);
    private final DeliveryLogRepository deliveryLogRepository = mock(DeliveryLogRepository.class);
    private final ApplicationConfigService configService = mock(ApplicationConfigService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T08:00:00Z"), ZoneOffset.UTC);
    private final SnapshotCleanupService service =
            new SnapshotCleanupService(snapshotRepository, deliveryLogRepository, configService, clock);

    private static final LocalDateTime EXPECTED_CUTOFF = LocalDateTime.parse("2026-06-20T08:00:00");

    @Test
    void deletesSnapshotsOlderThanConfiguredRetention() {
        when(configService.getSnapshotRetentionDays()).thenReturn(30);
        when(snapshotRepository.deleteFetchedBefore(EXPECTED_CUTOFF)).thenReturn(123);

        assertThat(service.cleanupExpiredSnapshots()).isEqualTo(123);
        verify(snapshotRepository).deleteFetchedBefore(EXPECTED_CUTOFF);
    }

    @Test
    void deletesDeliveryLogsOlderThanConfiguredRetention() {
        when(configService.getSnapshotRetentionDays()).thenReturn(30);
        when(deliveryLogRepository.deleteDeliveredBefore(EXPECTED_CUTOFF)).thenReturn(45);

        assertThat(service.cleanupExpiredDeliveryLogs()).isEqualTo(45);
        verify(deliveryLogRepository).deleteDeliveredBefore(EXPECTED_CUTOFF);
    }

    @Test
    void cleanupExpiredDataCoversBothStores() {
        when(configService.getSnapshotRetentionDays()).thenReturn(7);

        service.cleanupExpiredData();

        LocalDateTime cutoff = LocalDateTime.parse("2026-07-13T08:00:00");
        verify(snapshotRepository).deleteFetchedBefore(cutoff);
        verify(deliveryLogRepository).deleteDeliveredBefore(cutoff);
    }
}
