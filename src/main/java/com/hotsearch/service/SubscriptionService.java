package com.hotsearch.service;

import com.hotsearch.dto.SubscriptionRequest;
import com.hotsearch.dto.SubscriptionResponse;
import com.hotsearch.entity.Subscription;
import com.hotsearch.repository.SubscriptionRepository;
import com.hotsearch.repository.ChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ChannelRepository channelRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, ChannelRepository channelRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.channelRepository = channelRepository;
    }

    public List<SubscriptionResponse> listByUser(Long userId) {
        return subscriptionRepository.findByUserId(userId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public SubscriptionResponse create(Long userId, SubscriptionRequest req) {
        Subscription sub = new Subscription();
        sub.setUserId(userId);
        sub.setName(req.name());
        sub.setKeywords(req.keywords() != null ? req.keywords() : List.of());
        sub.setExcludeKeywords(req.excludeKeywords() != null ? req.excludeKeywords() : List.of());
        sub.setLabels(req.labels() != null ? req.labels() : List.of());
        sub.setMinHotValue(req.minHotValue());
        sub.setChannelIds(validateChannelIds(userId, req.channelIds()));
        sub.setEnabled(req.enabled() != null ? req.enabled() : true);
        return toResponse(subscriptionRepository.save(sub));
    }

    @Transactional
    public SubscriptionResponse update(Long userId, Long id, SubscriptionRequest req) {
        Subscription sub = subscriptionRepository.findById(id)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("订阅不存在"));
        sub.setName(req.name());
        sub.setKeywords(req.keywords() != null ? req.keywords() : List.of());
        sub.setExcludeKeywords(req.excludeKeywords() != null ? req.excludeKeywords() : List.of());
        sub.setLabels(req.labels() != null ? req.labels() : List.of());
        sub.setMinHotValue(req.minHotValue());
        if (req.channelIds() != null) sub.setChannelIds(validateChannelIds(userId, req.channelIds()));
        sub.setEnabled(req.enabled());
        return toResponse(subscriptionRepository.save(sub));
    }

    @Transactional
    public SubscriptionResponse updateEnabled(Long userId, Long id, boolean enabled) {
        Subscription sub = subscriptionRepository.findById(id)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("订阅不存在"));
        sub.setEnabled(enabled);
        return toResponse(subscriptionRepository.save(sub));
    }

    public SubscriptionResponse getById(Long userId, Long id) {
        Subscription sub = subscriptionRepository.findById(id)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("订阅不存在"));
        return toResponse(sub);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Subscription sub = subscriptionRepository.findById(id)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("订阅不存在"));
        subscriptionRepository.delete(sub);
    }

    private List<Long> validateChannelIds(Long userId, List<Long> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalized = channelIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        long validCount = channelRepository.countByUserIdAndIdIn(userId, normalized);
        if (validCount != normalized.size()) {
            throw new RuntimeException("订阅包含不存在或无权限的推送通道");
        }
        return normalized;
    }
    private SubscriptionResponse toResponse(Subscription s) {
        return new SubscriptionResponse(s.getId(), s.getName(), s.getKeywords(),
                s.getExcludeKeywords(), s.getLabels(), s.getMinHotValue(), s.getChannelIds(), s.getEnabled(), s.getCreatedAt());
    }

    public List<Subscription> listAllEnabled() {
        return subscriptionRepository.findByEnabledTrue();
    }
}
