package com.hotsearch.repository;

import com.hotsearch.entity.DeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long> {
    List<DeliveryLog> findByDeliveredAtAfterOrderByDeliveredAtDesc(LocalDateTime since);
    boolean existsByKeywordAndChannelIdAndDeliveredAtAfter(String keyword, Long channelId, LocalDateTime since);
    
    // Find logs by channel IDs belonging to a specific user
    List<DeliveryLog> findByChannelIdInAndDeliveredAtAfterOrderByDeliveredAtDesc(List<Long> channelIds, LocalDateTime since);
}
