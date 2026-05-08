package com.hotsearch.service;

import com.hotsearch.dto.SubscriptionRequest;
import com.hotsearch.dto.SubscriptionResponse;
import com.hotsearch.entity.Subscription;
import com.hotsearch.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public List<SubscriptionResponse> listByUser(Long userId) {
        return subscriptionRepository.findByUserId(userId).stream()
                .map(this::toResponse).toList();
    }

    public SubscriptionResponse create(Long userId, SubscriptionRequest req) {
        Subscription sub = new Subscription();
        sub.setUserId(userId);
        sub.setName(req.name());
        sub.setKeywords(req.keywords() != null ? req.keywords() : List.of());
        sub.setExcludeKeywords(req.excludeKeywords() != null ? req.excludeKeywords() : List.of());
        sub.setLabels(req.labels() != null ? req.labels() : List.of());
        sub.setMinHotValue(req.minHotValue());
        sub.setEnabled(req.enabled() != null ? req.enabled() : true);
        return toResponse(subscriptionRepository.save(sub));
    }

    public SubscriptionResponse update(Long userId, Long id, SubscriptionRequest req) {
        Subscription sub = subscriptionRepository.findById(id)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("订阅不存在"));
        sub.setName(req.name());
        sub.setKeywords(req.keywords() != null ? req.keywords() : List.of());
        sub.setExcludeKeywords(req.excludeKeywords() != null ? req.excludeKeywords() : List.of());
        sub.setLabels(req.labels() != null ? req.labels() : List.of());
        sub.setMinHotValue(req.minHotValue());
        sub.setEnabled(req.enabled());
        return toResponse(subscriptionRepository.save(sub));
    }

    public void delete(Long userId, Long id) {
        Subscription sub = subscriptionRepository.findById(id)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("订阅不存在"));
        subscriptionRepository.delete(sub);
    }

    private SubscriptionResponse toResponse(Subscription s) {
        return new SubscriptionResponse(s.getId(), s.getName(), s.getKeywords(),
                s.getExcludeKeywords(), s.getLabels(), s.getMinHotValue(), s.getEnabled(), s.getCreatedAt());
    }

    public List<Subscription> listAllEnabled() {
        return subscriptionRepository.findByEnabledTrue();
    }
}
