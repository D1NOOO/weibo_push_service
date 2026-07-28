package com.hotsearch.service;

import com.hotsearch.dto.DeliveryLogEntry;
import com.hotsearch.entity.Channel;
import com.hotsearch.entity.DeliveryLog;
import com.hotsearch.repository.ChannelRepository;
import com.hotsearch.repository.DeliveryLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryServiceTest {

    private final DeliveryLogRepository deliveryLogRepository = mock(DeliveryLogRepository.class);
    private final ChannelRepository channelRepository = mock(ChannelRepository.class);
    private final DeliveryService service = new DeliveryService(deliveryLogRepository, channelRepository);

    @Test
    void clearByUserReturnsDeletedLogCount() {
        when(channelRepository.findByUserId(10L)).thenReturn(List.of(channel(1L), channel(2L)));
        when(deliveryLogRepository.deleteByChannelIdIn(List.of(1L, 2L))).thenReturn(7);

        assertThat(service.clearByUser(10L)).isEqualTo(7);
    }

    @Test
    void clearByUserWithoutChannelsDeletesNothing() {
        when(channelRepository.findByUserId(10L)).thenReturn(List.of());

        assertThat(service.clearByUser(10L)).isZero();
        verify(deliveryLogRepository, never()).deleteByChannelIdIn(anyList());
    }

    @Test
    void groupsLogsByKeywordKeepingLatestMetadata() {
        Channel channel = channel(1L);
        when(channelRepository.findByUserId(10L)).thenReturn(List.of(channel));
        // 仓库按 deliveredAt 倒序返回
        DeliveryLog newest = log("世界杯", "SUCCESS", "2026-07-20T10:00:00");
        DeliveryLog other = log("演唱会", "SUCCESS", "2026-07-20T09:30:00");
        DeliveryLog older = log("世界杯", "FAILED", "2026-07-20T09:00:00");
        when(deliveryLogRepository.findByChannelIdInAndDeliveredAtAfterOrderByDeliveredAtDesc(anyList(), any()))
                .thenReturn(List.of(newest, other, older));

        List<DeliveryLogEntry> entries = service.getRecentByUser(10L, 24);

        assertThat(entries).extracting(DeliveryLogEntry::keyword)
                .containsExactly("世界杯", "演唱会");
        DeliveryLogEntry worldCup = entries.get(0);
        assertThat(worldCup.deliveredAt()).isEqualTo(Instant.parse("2026-07-20T10:00:00Z"));
        assertThat(worldCup.channels()).hasSize(2);
        assertThat(worldCup.channels().get(0).provider()).isEqualTo("wechat");
    }

    private static Channel channel(Long id) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setUserId(10L);
        channel.setProvider("wechat");
        return channel;
    }

    private static DeliveryLog log(String keyword, String status, String deliveredAt) {
        DeliveryLog log = new DeliveryLog();
        log.setChannelId(1L);
        log.setSubscriptionId(1L);
        log.setKeyword(keyword);
        log.setStatus(status);
        log.setDeliveredAt(LocalDateTime.parse(deliveredAt));
        return log;
    }
}
