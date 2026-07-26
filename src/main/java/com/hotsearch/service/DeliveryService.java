package com.hotsearch.service;

import com.hotsearch.dto.DeliveryLogEntry;
import com.hotsearch.entity.Channel;
import com.hotsearch.entity.DeliveryLog;
import com.hotsearch.repository.ChannelRepository;
import com.hotsearch.repository.DeliveryLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** 清空当前用户的全部推送日志（同时重置其去重状态），返回删除的日志条数。 */
    @Transactional
    public int clearByUser(Long userId) {
        List<Channel> userChannels = channelRepository.findByUserId(userId);
        if (userChannels.isEmpty()) return 0;
        List<Long> channelIds = userChannels.stream().map(Channel::getId).toList();
        return deliveryLogRepository.deleteByChannelIdIn(channelIds);
    }

    public boolean isDuplicate(String keyword, Long channelId, String target, LocalDateTime since) {
        return deliveryLogRepository.existsByKeywordAndChannelIdAndTargetAndStatusAndDeliveredAtAfter(
                keyword, channelId, target, "SUCCESS", since);
    }

    public List<DeliveryLogEntry> getRecentByUser(Long userId, int hours) {
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours);

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
        // logs 已按 deliveredAt 倒序，分组保持相遇顺序：每组第一条即该关键词最新一次投递
        Map<String, List<DeliveryLog>> groupedByKw = logs.stream()
                .collect(Collectors.groupingBy(
                        DeliveryLog::getKeyword,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return groupedByKw.entrySet().stream()
                .map(entry -> {
                    List<DeliveryLog> kwLogs = entry.getValue();
                    DeliveryLog latest = kwLogs.get(0);

                    List<DeliveryLogEntry.ChannelDelivery> channelDeliveries = new ArrayList<>();
                    for (DeliveryLog log : kwLogs) {
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
                            entry.getKey(),
                            latest.getLabel(),
                            latest.getHotValue(),
                            channelDeliveries,
                            latest.getDeliveredAt()
                    );
                })
                .toList();
    }
}
