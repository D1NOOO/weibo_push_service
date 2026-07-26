package com.hotsearch.service;

import com.hotsearch.repository.HotSearchSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@Transactional
public class SnapshotCleanupService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotCleanupService.class);

    private final HotSearchSnapshotRepository snapshotRepository;
    private final ApplicationConfigService configService;
    private final Clock clock;

    @Autowired
    public SnapshotCleanupService(HotSearchSnapshotRepository snapshotRepository,
                                  ApplicationConfigService configService) {
        this(snapshotRepository, configService, Clock.systemUTC());
    }

    SnapshotCleanupService(HotSearchSnapshotRepository snapshotRepository,
                           ApplicationConfigService configService, Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.configService = configService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOnStartup() {
        cleanupExpiredSnapshots();
    }

    @Scheduled(cron = "${app.snapshot.cleanup-cron:0 30 3 * * *}",
            zone = "${app.schedule.zone:Asia/Shanghai}")
    public void scheduledCleanup() {
        cleanupExpiredSnapshots();
    }

    public int cleanupExpiredSnapshots() {
        int retentionDays = configService.getSnapshotRetentionDays();
        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .minusDays(retentionDays);
        int deleted = snapshotRepository.deleteFetchedBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} hot-search snapshots older than {} days", deleted, retentionDays);
        }
        return deleted;
    }
}
