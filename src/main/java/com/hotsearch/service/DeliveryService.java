package com.hotsearch.service;

import com.hotsearch.dto.DeliveryBatchResponse;
import com.hotsearch.entity.DeliveryLog;
import com.hotsearch.repository.ChannelRepository;
import com.hotsearch.repository.DeliveryLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
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

    public List<DeliveryBatchResponse> getRecentBatches(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<DeliveryLog> logs = deliveryLogRepository.findByDeliveredAtAfterOrderByDeliveredAtDesc(since);
        return groupIntoBatches(logs);
    }

    public List<DeliveryBatchResponse> getRecentBatchesByUser(Long userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);

        List<Long> userChannelIds = channelRepository.findByUserId(userId)
                .stream()
                .map(ch -> ch.getId())
                .collect(Collectors.toList());

        if (userChannelIds.isEmpty()) return List.of();

        List<DeliveryLog> logs = deliveryLogRepository.findByChannelIdInAndDeliveredAtAfterOrderByDeliveredAtDesc(userChannelIds, since);
        return groupIntoBatches(logs);
    }

    private List<DeliveryBatchResponse> groupIntoBatches(List<DeliveryLog> logs) {
        Map<String, List<DeliveryLog>> grouped = logs.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getBatchId() != null ? l.getBatchId() : "legacy_" + l.getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return grouped.values().stream()
                .map(batch -> {
                    DeliveryLog first = batch.get(0);
                    List<DeliveryBatchResponse.KeywordEntry> keywords = batch.stream()
                            .map(l -> new DeliveryBatchResponse.KeywordEntry(l.getKeyword(), l.getLabel(), l.getHotValue()))
                            .toList();
                    return new DeliveryBatchResponse(
                            first.getBatchId(),
                            first.getSubscriptionId(),
                            first.getChannelId(),
                            first.getStatus(),
                            first.getError(),
                            keywords,
                            first.getDeliveredAt()
                    );
                })
                .toList();
    }
}
