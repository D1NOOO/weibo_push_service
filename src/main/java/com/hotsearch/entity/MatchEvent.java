package com.hotsearch.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 命中事件：某订阅命中某话题的一轮生命周期。
 * 同一 userId+subscriptionId+normalizedKeyword 在活跃窗口内的重复命中只更新本事件，
 * 避免抓取频率放大命中次数（product-plan §7）。
 */
@Entity
@Table(name = "match_events", indexes = {
        @Index(name = "idx_match_events_user_seen", columnList = "user_id,last_seen_at"),
        @Index(name = "idx_match_events_dedupe", columnList = "user_id,subscription_id,normalized_keyword,last_seen_at")
})
public class MatchEvent {

    public static final String DELIVERY_NONE = "NONE";
    public static final String DELIVERY_SENT = "SENT";
    public static final String DELIVERY_FAILED = "FAILED";
    public static final String DELIVERY_NO_QUOTA = "NO_QUOTA";
    public static final String DELIVERY_NO_CHANNEL = "NO_CHANNEL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "subscription_name")
    private String subscriptionName;

    @Column(nullable = false)
    private String keyword;

    @Column(name = "normalized_keyword", nullable = false)
    private String normalizedKeyword;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "observed_count", nullable = false)
    private Integer observedCount = 1;

    @Column(name = "first_rank")
    private Integer firstRank;

    @Column(name = "best_rank")
    private Integer bestRank;

    @Column(name = "latest_rank")
    private Integer latestRank;

    @Column(name = "max_hot_value")
    private Long maxHotValue;

    @Column(name = "latest_hot_value")
    private Long latestHotValue;

    @Column(name = "latest_label")
    private String latestLabel;

    /** 微信订阅消息投递状态：NONE / SENT / FAILED / NO_QUOTA / NO_CHANNEL */
    @Column(name = "delivery_status", nullable = false)
    private String deliveryStatus = DELIVERY_NONE;

    /** 最近一次触发通知的原因：NEW_EVENT / LABEL_UPGRADE / RANK_TOP / HOT_SURGE */
    @Column(name = "notify_reason")
    private String notifyReason;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "delivery_error", length = 512)
    private String deliveryError;

    public MatchEvent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
    public String getSubscriptionName() { return subscriptionName; }
    public void setSubscriptionName(String subscriptionName) { this.subscriptionName = subscriptionName; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getNormalizedKeyword() { return normalizedKeyword; }
    public void setNormalizedKeyword(String normalizedKeyword) { this.normalizedKeyword = normalizedKeyword; }
    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Integer getObservedCount() { return observedCount; }
    public void setObservedCount(Integer observedCount) { this.observedCount = observedCount; }
    public Integer getFirstRank() { return firstRank; }
    public void setFirstRank(Integer firstRank) { this.firstRank = firstRank; }
    public Integer getBestRank() { return bestRank; }
    public void setBestRank(Integer bestRank) { this.bestRank = bestRank; }
    public Integer getLatestRank() { return latestRank; }
    public void setLatestRank(Integer latestRank) { this.latestRank = latestRank; }
    public Long getMaxHotValue() { return maxHotValue; }
    public void setMaxHotValue(Long maxHotValue) { this.maxHotValue = maxHotValue; }
    public Long getLatestHotValue() { return latestHotValue; }
    public void setLatestHotValue(Long latestHotValue) { this.latestHotValue = latestHotValue; }
    public String getLatestLabel() { return latestLabel; }
    public void setLatestLabel(String latestLabel) { this.latestLabel = latestLabel; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public String getNotifyReason() { return notifyReason; }
    public void setNotifyReason(String notifyReason) { this.notifyReason = notifyReason; }
    public LocalDateTime getNotifiedAt() { return notifiedAt; }
    public void setNotifiedAt(LocalDateTime notifiedAt) { this.notifiedAt = notifiedAt; }
    public String getDeliveryError() { return deliveryError; }
    public void setDeliveryError(String deliveryError) { this.deliveryError = deliveryError; }
}
