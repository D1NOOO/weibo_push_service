package com.hotsearch.service;

import com.hotsearch.dto.DeliveryLogResponse;
import com.hotsearch.entity.DeliveryLog;
import com.hotsearch.repository.ChannelRepository;
import com.hotsearch.repository.DeliveryLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeliveryService {

    private final DeliveryLogRepository deliveryLogRepository;
    private final ChannelRepository channelRepository;

    public DeliveryService(DeliveryLogRepository deliveryLogRepository, ChannelRepository channelRepository) {
        this.deliveryLogRepository = deliveryLogRepository;
        this.channelRepository = channelRepository;
    }

    public DeliveryLog save(DeliveryLog log) {
        return deliveryLogRepository.save(log);
    }

    public boolean isDuplicate(String keyword, Long channelId, LocalDateTime since) {
        return deliveryLogRepository.existsByKeywordAndChannelIdAndDeliveredAtAfter(keyword, channelId, since);
    }

    public List<DeliveryLogResponse> getRecentLogs(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return deliveryLogRepository.findByDeliveredAtAfterOrderByDeliveredAtDesc(since).stream()
                .map(l -> new DeliveryLogResponse(l.getId(), l.getSubscriptionId(), l.getChannelId(),
                        l.getKeyword(), l.getLabel(), l.getHotValue(), l.getStatus(), l.getError(), l.getDeliveredAt()))
                .toList();
    }

    public List<DeliveryLogResponse> getRecentLogsByUser(Long userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<Long> userChannelIds = channelRepository.findByUserId(userId)
                .stream()
                .map(ch -> ch.getId())
                .collect(Collectors.toList());
        
        if (userChannelIds.isEmpty()) return List.of();
        
        return deliveryLogRepository.findByChannelIdInAndDeliveredAtAfterOrderByDeliveredAtDesc(userChannelIds, since)
                .stream()
                .map(l -> new DeliveryLogResponse(l.getId(), l.getSubscriptionId(), l.getChannelId(),
                        l.getKeyword(), l.getLabel(), l.getHotValue(), l.getStatus(), l.getError(), l.getDeliveredAt()))
                .toList();
    }
}
