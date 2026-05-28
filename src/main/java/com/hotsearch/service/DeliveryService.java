package com.hotsearch.service;

import com.hotsearch.dto.DeliveryLogEntry;
import com.hotsearch.entity.Channel;
import com.hotsearch.entity.DeliveryLog;
import com.hotsearch.repository.ChannelRepository;
import com.hotsearch.repository.DeliveryLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public void clearAll() {
        deliveryLogRepository.deleteAll();
    }

    @Transactional
    public int clearByUser(Long userId) {
        List<Channel> userChannels = channelRepository.findByUserId(userId);
        if (userChannels.isEmpty()) return 0;
        List<Long> channelIds = userChannels.stream().map(Channel::getId).toList();
        deliveryLogRepository.deleteByChannelIdIn(channelIds);
        return channelIds.size();
    }

    public boolean isDuplicate(String keyword, Long channelId, LocalDateTime since) {
        return deliveryLogRepository.existsByKeywordAndChannelIdAndDeliveredAtAfter(keyword, channelId, since);
    }

    public boolean isDuplicate(String keyword, Long channelId, String target, LocalDateTime since) {
        return deliveryLogRepository.existsByKeywordAndChannelIdAndTargetAndStatusAndDeliveredAtAfter(
                keyword, channelId, target, "SUCCESS", since);
    }

    public List<DeliveryLogEntry> getRecentByUser(Long userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);

        List<Channel> userChannels = channelRepository.findByUserId(userId);
        if (userChannels.isEmpty()) return List.of();

        Map<Long, Channel> channelMap = userChannels.stream()
                .collect(Collectors.toMap(Channel::getId, ch -> ch));

        List<Long> userChannelIds = new ArrayList<>(channelMap.keySet());

        List<DeliveryLog> logs = deliveryLogRepository
                .findByChannelIdInAndDeliveredAtAfterOrderByDeliveredAtDesc(userChannelIds, since);

        return groupByKeyword(logs, channelMap);
    }

    private List<DeliveryLogEntry> groupByKeyword(List<DeliveryLog> logs,
                                                   Map<Long, Channel> channelMap) {
        // Group by keyword, keep all delivery attempts per channel+target
        Map<String, List<DeliveryLog>> groupedByKw = logs.stream()
                .collect(Collectors.groupingBy(
                        DeliveryLog::getKeyword,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return groupedByKw.entrySet().stream()
                .map(entry -> {
                    String keyword = entry.getKey();
                    List<DeliveryLog> kwLogs = entry.getValue();

                    List<DeliveryLogEntry.ChannelDelivery> channelDeliveries = new ArrayList<>();
                    DeliveryLog latest = kwLogs.get(0);

                    for (DeliveryLog log : kwLogs) {
                        if (log.getDeliveredAt().isAfter(latest.getDeliveredAt())) {
                            latest = log;
                        }
                        Channel ch = channelMap.get(log.getChannelId());
                        String provider = ch != null ? ch.getProvider() : "unknown";
                        channelDeliveries.add(new DeliveryLogEntry.ChannelDelivery(
                                provider,
                                log.getTarget(),
                                log.getStatus(),
                                log.getError(),
                                log.getDeliveredAt()
                        ));
                    }

                    return new DeliveryLogEntry(
                            keyword,
                            latest.getLabel(),
                            latest.getHotValue(),
                            channelDeliveries,
                            latest.getDeliveredAt()
                    );
                })
                .toList();
    }
}
