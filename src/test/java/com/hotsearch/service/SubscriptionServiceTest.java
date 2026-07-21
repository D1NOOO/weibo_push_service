package com.hotsearch.service;

import com.hotsearch.dto.SubscriptionRequest;
import com.hotsearch.dto.SubscriptionResponse;
import com.hotsearch.entity.Subscription;
import com.hotsearch.repository.ChannelRepository;
import com.hotsearch.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {

    private final SubscriptionRepository repository = mock(SubscriptionRepository.class);
    private final ChannelRepository channelRepository = mock(ChannelRepository.class);
    private final SubscriptionService service = new SubscriptionService(repository, channelRepository);

    @Test
    void storesValidityAsUtcAndReturnsInstants() {
        Instant startAt = Instant.parse("2026-07-21T02:00:05Z");
        Instant endAt = Instant.parse("2026-07-21T03:30:45Z");
        when(repository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionResponse response = service.create(1L, request(startAt, endAt));

        assertThat(response.startAt()).isEqualTo(startAt);
        assertThat(response.endAt()).isEqualTo(endAt);
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(subscription ->
                subscription.getStartAt().equals(LocalDateTime.parse("2026-07-21T02:00:05"))
                        && subscription.getEndAt().equals(LocalDateTime.parse("2026-07-21T03:30:45"))));
    }

    @Test
    void rejectsEndThatIsNotAfterStart() {
        Instant time = Instant.parse("2026-07-21T02:00:05Z");

        assertThatThrownBy(() -> service.create(1L, request(time, time)))
                .hasMessage("结束时间必须晚于开始时间");
        verify(repository, never()).save(any());
    }

    private SubscriptionRequest request(Instant startAt, Instant endAt) {
        return new SubscriptionRequest("测试规则", List.of("热搜"), List.of(), List.of(),
                null, List.of(), true, startAt, endAt);
    }
}
