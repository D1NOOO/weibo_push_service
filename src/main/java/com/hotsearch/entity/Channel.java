package com.hotsearch.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "channels")
public class Channel {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String provider;

    @Column(columnDefinition = "TEXT")
    private String config;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Channel() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getConfigMap() {
        if (config == null || config.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(config, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    public void setConfigMap(Map<String, Object> configMap) {
        try {
            this.config = MAPPER.writeValueAsString(configMap);
        } catch (JsonProcessingException e) {
            this.config = "{}";
        }
    }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
}
