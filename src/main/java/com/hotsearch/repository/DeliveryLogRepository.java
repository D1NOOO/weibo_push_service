package com.hotsearch.repository;

import com.hotsearch.entity.DeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long> {

    boolean existsByKeywordAndChannelIdAndTargetAndStatusAndDeliveredAtAfter(
            String keyword, Long channelId, String target, String status, LocalDateTime since);

    // Find logs by channel IDs belonging to a specific user
    List<DeliveryLog> findByChannelIdInAndDeliveredAtAfterOrderByDeliveredAtDesc(List<Long> channelIds, LocalDateTime since);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DeliveryLog deliveryLog where deliveryLog.channelId in :channelIds")
    int deleteByChannelIdIn(@Param("channelIds") List<Long> channelIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DeliveryLog deliveryLog where deliveryLog.deliveredAt < :cutoff")
    int deleteDeliveredBefore(@Param("cutoff") LocalDateTime cutoff);
}
