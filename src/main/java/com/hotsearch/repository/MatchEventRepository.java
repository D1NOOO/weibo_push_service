package com.hotsearch.repository;

import com.hotsearch.entity.MatchEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchEventRepository extends JpaRepository<MatchEvent, Long> {

    Optional<MatchEvent> findTopByUserIdAndSubscriptionIdAndNormalizedKeywordOrderByLastSeenAtDesc(
            Long userId, Long subscriptionId, String normalizedKeyword);

    List<MatchEvent> findByUserIdAndLastSeenAtAfterOrderByLastSeenAtDesc(Long userId, LocalDateTime since);

    List<MatchEvent> findByUserIdAndSubscriptionIdAndLastSeenAtAfterOrderByLastSeenAtDesc(
            Long userId, Long subscriptionId, LocalDateTime since);

    long countByUserIdAndFirstSeenAtAfter(Long userId, LocalDateTime since);
}
