package com.hotsearch.entity;

import com.hotsearch.entity.converter.LongListJsonConverter;
import com.hotsearch.entity.converter.StringListJsonConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscriptions_user_end", columnList = "user_id,end_at"),
        @Index(name = "idx_subscriptions_enabled_window", columnList = "enabled,start_at,end_at")
})
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "keywords", columnDefinition = "TEXT")
    private List<String> keywords = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "exclude_keywords", columnDefinition = "TEXT")
    private List<String> excludeKeywords = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "labels", columnDefinition = "TEXT")
    private List<String> labels = new ArrayList<>();

    @Convert(converter = LongListJsonConverter.class)
    @Column(name = "channel_ids", columnDefinition = "TEXT")
    private List<Long> channelIds = new ArrayList<>();

    @Column(name = "min_hot_value")
    private Integer minHotValue;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    public Subscription() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getMinHotValue() { return minHotValue; }
    public void setMinHotValue(Integer minHotValue) { this.minHotValue = minHotValue; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }

    public boolean isEffectiveAtUtc(LocalDateTime utcNow) {
        return (startAt == null || !startAt.isAfter(utcNow))
                && (endAt == null || endAt.isAfter(utcNow));
    }

    public List<String> getKeywords() {
        return keywords == null ? new ArrayList<>() : keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords == null ? new ArrayList<>() : new ArrayList<>(keywords);
    }

    public List<String> getExcludeKeywords() {
        return excludeKeywords == null ? new ArrayList<>() : excludeKeywords;
    }

    public void setExcludeKeywords(List<String> excludeKeywords) {
        this.excludeKeywords = excludeKeywords == null ? new ArrayList<>() : new ArrayList<>(excludeKeywords);
    }

    public List<String> getLabels() {
        return labels == null ? new ArrayList<>() : labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels == null ? new ArrayList<>() : new ArrayList<>(labels);
    }

    public List<Long> getChannelIds() {
        return channelIds == null ? new ArrayList<>() : channelIds;
    }

    public void setChannelIds(List<Long> channelIds) {
        this.channelIds = channelIds == null ? new ArrayList<>() : new ArrayList<>(channelIds);
    }
}
