package com.hotsearch.repository;

import com.hotsearch.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserId(Long userId);
    List<Subscription> findByEnabledTrue();

    @Query("""
            select subscription from Subscription subscription
            where subscription.userId = :userId
              and (subscription.endAt is null or subscription.endAt > :now)
            order by subscription.createdAt desc
            """)
    List<Subscription> findCurrentByUserId(@Param("userId") Long userId,
                                           @Param("now") LocalDateTime now);

    @Query("""
            select subscription from Subscription subscription
            where subscription.userId = :userId
              and subscription.endAt is not null
              and subscription.endAt <= :now
            order by subscription.endAt desc
            """)
    List<Subscription> findExpiredByUserId(@Param("userId") Long userId,
                                           @Param("now") LocalDateTime now);

    @Query("""
            select subscription from Subscription subscription
            where subscription.enabled = true
              and (subscription.startAt is null or subscription.startAt <= :now)
              and (subscription.endAt is null or subscription.endAt > :now)
            """)
    List<Subscription> findAllEffectiveEnabled(@Param("now") LocalDateTime now);
}
