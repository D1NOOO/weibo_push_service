package com.hotsearch.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_logs")
public class DeliveryLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(nullable = false)
    private String keyword;

    private String label;

    @Column(name = "hot_value")
    private Long hotValue;

    @Column(nullable = false)
    private String status;

    private String error;

    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "delivered_at", nullable = false)
    private LocalDateTime deliveredAt;

    public DeliveryLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Long getHotValue() { return hotValue; }
    public void setHotValue(Long hotValue) { this.hotValue = hotValue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
}
