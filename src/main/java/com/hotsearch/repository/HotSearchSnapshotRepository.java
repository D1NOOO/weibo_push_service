package com.hotsearch.repository;

import com.hotsearch.entity.HotSearchSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HotSearchSnapshotRepository extends JpaRepository<HotSearchSnapshot, Long> {
    Optional<HotSearchSnapshot> findTopByOrderByFetchedAtDesc();
    List<HotSearchSnapshot> findByFetchedAtAfterOrderByFetchedAtDesc(LocalDateTime since);
    List<HotSearchSnapshot> findTop50ByOrderByFetchedAtDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from HotSearchSnapshot snapshot where snapshot.fetchedAt < :cutoff")
    int deleteFetchedBefore(@Param("cutoff") LocalDateTime cutoff);
}
