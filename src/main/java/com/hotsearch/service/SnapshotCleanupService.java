package com.hotsearch.service;

import com.hotsearch.repository.DeliveryLogRepository;
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

/**
 * 统一数据保留清理：按 retention-days 定期删除过期的热搜快照与推送日志。
 */
@Service
@Transactional
public class SnapshotCleanupService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotCleanupService.class);

    private final HotSearchSnapshotRepository snapshotRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final ApplicationConfigService configService;
    private final Clock clock;

    @Autowired
    public SnapshotCleanupService(HotSearchSnapshotRepository snapshotRepository,
                                  DeliveryLogRepository deliveryLogRepository,
                                  ApplicationConfigService configService) {
        this(snapshotRepository, deliveryLogRepository, configService, Clock.systemUTC());
    }

    SnapshotCleanupService(HotSearchSnapshotRepository snapshotRepository,
                           DeliveryLogRepository deliveryLogRepository,
                           ApplicationConfigService configService, Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.configService = configService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOnStartup() {
        cleanupExpiredData();
    }

    @Scheduled(cron = "${app.snapshot.cleanup-cron:0 30 3 * * *}",
            zone = "${app.schedule.zone:Asia/Shanghai}")
    public void scheduledCleanup() {
        cleanupExpiredData();
    }

    public void cleanupExpiredData() {
        cleanupExpiredSnapshots();
        cleanupExpiredDeliveryLogs();
    }

    public int cleanupExpiredSnapshots() {
        int retentionDays = configService.getSnapshotRetentionDays();
        int deleted = snapshotRepository.deleteFetchedBefore(cutoff(retentionDays));
        if (deleted > 0) {
            log.info("Deleted {} hot-search snapshots older than {} days", deleted, retentionDays);
        }
        return deleted;
    }

    public int cleanupExpiredDeliveryLogs() {
        int retentionDays = configService.getSnapshotRetentionDays();
        int deleted = deliveryLogRepository.deleteDeliveredBefore(cutoff(retentionDays));
        if (deleted > 0) {
            log.info("Deleted {} delivery logs older than {} days", deleted, retentionDays);
        }
        return deleted;
    }

    private LocalDateTime cutoff(int retentionDays) {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(retentionDays);
    }
}
