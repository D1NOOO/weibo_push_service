package com.hotsearch.service;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import com.hotsearch.entity.DeliveryLog;
import com.hotsearch.entity.Subscription;
import com.hotsearch.matcher.SubscriptionMatcher.MatchResult;
import com.hotsearch.provider.MessageProvider;
import com.hotsearch.provider.ProviderException;
import com.hotsearch.provider.PushMessage;
import com.hotsearch.provider.RateLimitedException;
import com.hotsearch.service.DeliveryPlanner.SubscriptionDelivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryExecutorTest {

    private static final LocalDateTime SINCE = LocalDateTime.parse("2026-07-20T04:00:00");

    private final DeliveryService deliveryService = mock(DeliveryService.class);
    private final SinkShortLinkService sinkShortLinkService = mock(SinkShortLinkService.class);
    private final MessageProvider provider = mock(MessageProvider.class);

    private final Subscription sub = subscription();
    private final Channel channel = channel();

    private DeliveryExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DeliveryExecutor(deliveryService, sinkShortLinkService,
                Map.of("wechat", provider), 3, 0);
        when(sinkShortLinkService.shortenItems(any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(provider.getTargets(any())).thenReturn(List.of("家人群"));
    }

    @Test
    void unknownProviderRecordsFailurePerMatch() {
        channel.setProvider("nosuch");

        executor.execute(delivery(item("A"), item("B")), SINCE);

        List<DeliveryLog> logs = savedLogs(2);
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.getStatus()).isEqualTo("FAILED");
            assertThat(log.getError()).contains("未知的推送提供者");
        });
    }

    @Test
    void emptyTargetsRecordsFailureInsteadOfSilentSkip() {
        when(provider.getTargets(any())).thenReturn(List.of());

        executor.execute(delivery(item("A")), SINCE);

        assertThat(savedLogs(1).get(0).getError()).isEqualTo("通道未配置目标聊天");
        verify(provider, never()).send(any());
    }

    @Test
    void dedupeFiltersAlreadyDeliveredKeywords() {
        when(deliveryService.isDuplicate(eq("A"), any(), anyString(), any())).thenReturn(true);
        when(deliveryService.isDuplicate(eq("B"), any(), anyString(), any())).thenReturn(false);

        executor.execute(delivery(item("A"), item("B")), SINCE);

        ArgumentCaptor<PushMessage> messageCaptor = ArgumentCaptor.forClass(PushMessage.class);
        verify(provider).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().items())
                .extracting(HotSearchItem::keyword).containsExactly("B");
        assertThat(messageCaptor.getValue().target()).isEqualTo("家人群");
        assertThat(messageCaptor.getValue().title()).isEqualTo("测试规则");

        List<DeliveryLog> logs = savedLogs(1);
        assertThat(logs.get(0).getKeyword()).isEqualTo("B");
        assertThat(logs.get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(logs.get(0).getTarget()).isEqualTo("家人群");
    }

    @Test
    void allDuplicatesSkipsSendEntirely() {
        when(deliveryService.isDuplicate(anyString(), any(), anyString(), any())).thenReturn(true);

        executor.execute(delivery(item("A")), SINCE);

        verify(provider, never()).send(any());
        verify(deliveryService, never()).save(any());
    }

    @Test
    void sendFailureRecordsFailedLogs() {
        doThrow(new ProviderException("上游挂了")).when(provider).send(any());

        executor.execute(delivery(item("A")), SINCE);

        List<DeliveryLog> logs = savedLogs(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(logs.get(0).getError()).isEqualTo("上游挂了");
    }

    @Test
    void rateLimitedRetriesUntilSuccess() {
        doThrow(new RateLimitedException("限频"))
                .doNothing()
                .when(provider).send(any());

        executor.execute(delivery(item("A")), SINCE);

        verify(provider, times(2)).send(any());
        assertThat(savedLogs(1).get(0).getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void rateLimitedExhaustsRetriesThenFailsWithOriginalError() {
        doThrow(new RateLimitedException("限频")).when(provider).send(any());

        executor.execute(delivery(item("A")), SINCE);

        // 首次 + 3 次重试
        verify(provider, times(4)).send(any());
        List<DeliveryLog> logs = savedLogs(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(logs.get(0).getError()).isEqualTo("限频");
    }

    private List<DeliveryLog> savedLogs(int expectedCount) {
        ArgumentCaptor<DeliveryLog> captor = ArgumentCaptor.forClass(DeliveryLog.class);
        verify(deliveryService, times(expectedCount)).save(captor.capture());
        return captor.getAllValues();
    }

    private SubscriptionDelivery delivery(HotSearchItem... items) {
        List<MatchResult> matches = List.of(items).stream()
                .map(item -> new MatchResult(sub, item))
                .toList();
        return new SubscriptionDelivery(sub, channel, matches);
    }

    private static Subscription subscription() {
        Subscription sub = new Subscription();
        sub.setId(1L);
        sub.setUserId(10L);
        sub.setName("测试规则");
        return sub;
    }

    private static Channel channel() {
        Channel channel = new Channel();
        channel.setId(100L);
        channel.setUserId(10L);
        channel.setProvider("wechat");
        return channel;
    }

    private static HotSearchItem item(String keyword) {
        return new HotSearchItem(1, keyword, "热", 12345L, false, "https://example.com");
    }
}
