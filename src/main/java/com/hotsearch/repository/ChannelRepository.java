package com.hotsearch.repository;

import com.hotsearch.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByUserId(Long userId);
    List<Channel> findByEnabledTrue();
}
