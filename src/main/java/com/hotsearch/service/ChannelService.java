package com.hotsearch.service;

import com.hotsearch.dto.ChannelRequest;
import com.hotsearch.dto.ChannelResponse;
import com.hotsearch.entity.Channel;
import com.hotsearch.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ChannelService {

    private final ChannelRepository channelRepository;

    public ChannelService(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    public List<ChannelResponse> listByUser(Long userId) {
        return channelRepository.findByUserId(userId).stream()
                .map(this::toResponse).toList();
    }

    public ChannelResponse create(Long userId, ChannelRequest req) {
        Channel ch = new Channel();
        ch.setUserId(userId);
        ch.setProvider(req.provider());
        ch.setConfigMap(req.config() != null ? req.config() : java.util.Map.of());
        ch.setEnabled(req.enabled() != null ? req.enabled() : true);
        return toResponse(channelRepository.save(ch));
    }

    @Transactional
    public ChannelResponse update(Long userId, Long id, ChannelRequest req) {
        Channel ch = channelRepository.findById(id)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("通道不存在"));
        ch.setProvider(req.provider());
        ch.setConfigMap(mergeConfig(ch.getConfigMap(), req.config()));
        ch.setEnabled(req.enabled());
        return toResponse(channelRepository.save(ch));
    }

    public ChannelResponse getById(Long userId, Long id) {
        Channel ch = channelRepository.findById(id)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("通道不存在"));
        return toResponse(ch, false);
    }

    public Channel getEntityById(Long userId, Long id) {
        return channelRepository.findById(id)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("通道不存在"));
    }

    @Transactional
    public ChannelResponse updateEnabled(Long userId, Long id, boolean enabled) {
        Channel ch = channelRepository.findById(id)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("通道不存在"));
        ch.setEnabled(enabled);
        return toResponse(channelRepository.save(ch));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Channel ch = channelRepository.findById(id)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("通道不存在"));
        channelRepository.delete(ch);
    }

    private ChannelResponse toResponse(Channel ch) {
        return toResponse(ch, false);
    }

    private ChannelResponse toResponse(Channel ch, boolean includeSecrets) {
        Map<String, Object> config = includeSecrets ? ch.getConfigMap() : maskConfig(ch.getConfigMap());
        return new ChannelResponse(ch.getId(), ch.getProvider(), config, ch.getEnabled(), ch.getCreatedAt());
    }

    private Map<String, Object> maskConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) return Map.of();
        Map<String, Object> masked = new LinkedHashMap<>();
        config.forEach((key, value) -> {
            if (isSecretKey(key)) {
                masked.put(key, maskValue(value));
            } else {
                masked.put(key, value);
            }
        });
        return masked;
    }

    private Map<String, Object> mergeConfig(Map<String, Object> existing, Map<String, Object> incoming) {
        if (incoming == null) return Map.of();
        Map<String, Object> merged = new LinkedHashMap<>(incoming);
        for (String key : incoming.keySet()) {
            Object value = incoming.get(key);
            if (isSecretKey(key) && shouldPreserveSecret(value) && existing != null && existing.containsKey(key)) {
                merged.put(key, existing.get(key));
            }
        }
        return merged;
    }

    private boolean isSecretKey(String key) {
        return Set.of("token", "webhookUrl", "webhook_url", "chatId", "apiToken", "secret", "appSecret", "app_secret", "receiveId").contains(key);
    }

    private boolean shouldPreserveSecret(Object value) {
        if (value == null) return true;
        String text = String.valueOf(value);
        return text.isBlank() || text.contains("...");
    }

    private String maskValue(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        if (text.isBlank()) return "";
        if (text.length() <= 8) return "******";
        return text.substring(0, 4) + "..." + text.substring(text.length() - 4);
    }

    public List<Channel> listAllEnabled() {
        return channelRepository.findByEnabledTrue();
    }
}
