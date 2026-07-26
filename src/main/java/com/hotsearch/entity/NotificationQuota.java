package com.hotsearch.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 微信订阅消息可发送额度。一次性订阅：用户授权一次，服务端可发送一条。
 */
@Entity
@Table(name = "notification_quota",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_quota_user_template",
                columnNames = {"user_id", "template_id"}))
public class NotificationQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(name = "remaining", nullable = false)
    private Integer remaining = 0;

    @Column(name = "total_granted", nullable = false)
    private Integer totalGranted = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public NotificationQuota() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public Integer getRemaining() { return remaining; }
    public void setRemaining(Integer remaining) { this.remaining = remaining; }
    public Integer getTotalGranted() { return totalGranted; }
    public void setTotalGranted(Integer totalGranted) { this.totalGranted = totalGranted; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
