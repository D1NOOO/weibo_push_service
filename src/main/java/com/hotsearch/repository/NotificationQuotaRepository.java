package com.hotsearch.repository;

import com.hotsearch.entity.NotificationQuota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationQuotaRepository extends JpaRepository<NotificationQuota, Long> {
    Optional<NotificationQuota> findByUserIdAndTemplateId(Long userId, String templateId);
    List<NotificationQuota> findByUserId(Long userId);
}
