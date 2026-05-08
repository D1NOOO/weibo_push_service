package com.hotsearch.repository;

import com.hotsearch.entity.HotSearchSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HotSearchSnapshotRepository extends JpaRepository<HotSearchSnapshot, Long> {
    Optional<HotSearchSnapshot> findTopByOrderByFetchedAtDesc();
    List<HotSearchSnapshot> findByFetchedAtAfterOrderByFetchedAtDesc(LocalDateTime since);
    List<HotSearchSnapshot> findTop50ByOrderByFetchedAtDesc();
}
