package com.hotsearch.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subscriptions")
public class Subscription {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywordsJson;

    @Column(name = "exclude_keywords", columnDefinition = "TEXT")
    private String excludeKeywordsJson;

    @Column(name = "labels", columnDefinition = "TEXT")
    private String labelsJson;

    @Column(name = "min_hot_value")
    private Integer minHotValue;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Subscription() {
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    private void serializeJson() {
        // JSON fields are set directly via setters
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

    public List<String> getKeywords() {
        return parseJsonList(keywordsJson);
    }
    public void setKeywords(List<String> keywords) {
        this.keywordsJson = toJson(keywords);
    }

    public List<String> getExcludeKeywords() {
        return parseJsonList(excludeKeywordsJson);
    }
    public void setExcludeKeywords(List<String> keywords) {
        this.excludeKeywordsJson = toJson(keywords);
    }

    public List<String> getLabels() {
        return parseJsonList(labelsJson);
    }
    public void setLabels(List<String> labels) {
        this.labelsJson = toJson(labels);
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private String toJson(List<String> list) {
        if (list == null) return "[]";
        try {
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
