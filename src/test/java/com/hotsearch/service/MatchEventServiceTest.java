package com.hotsearch.service;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.MatchEvent;
import com.hotsearch.entity.Subscription;
import com.hotsearch.repository.MatchEventRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchEventServiceTest {

    private final MatchEventRepository repository = mock(MatchEventRepository.class);
    private final MatchEventService service = new MatchEventService(repository, 6, 3, 2.0, "爆,沸");

    private Subscription subscription() {
        Subscription sub = new Subscription();
        sub.setId(10L);
        sub.setUserId(1L);
        sub.setName("测试规则");
        return sub;
    }

    private void mockNoExisting() {
        when(repository.findTopByUserIdAndSubscriptionIdAndNormalizedKeywordOrderByLastSeenAtDesc(
                anyLong(), anyLong(), anyString())).thenReturn(Optional.empty());
    }

    private void mockExisting(MatchEvent event) {
        when(repository.findTopByUserIdAndSubscriptionIdAndNormalizedKeywordOrderByLastSeenAtDesc(
                anyLong(), anyLong(), anyString())).thenReturn(Optional.of(event));
    }

    private void mockSavePassthrough() {
        when(repository.save(any(MatchEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MatchEvent activeEvent(String keyword) {
        MatchEvent event = new MatchEvent();
        event.setId(100L);
        event.setUserId(1L);
        event.setSubscriptionId(10L);
        event.setKeyword(keyword);
        event.setNormalizedKeyword(keyword.toLowerCase());
        event.setFirstSeenAt(LocalDateTime.now().minusHours(1));
        event.setLastSeenAt(LocalDateTime.now().minusMinutes(10));
        event.setObservedCount(3);
        event.setFirstRank(20);
        event.setBestRank(15);
        event.setLatestRank(18);
        event.setMaxHotValue(100_000L);
        event.setLatestHotValue(90_000L);
        event.setLatestLabel("热");
        return event;
    }

    @Test
    void firstHitCreatesEventWithNewEventReason() {
        mockNoExisting();
        mockSavePassthrough();

        var result = service.record(subscription(),
                new HotSearchItem(5, "周杰伦演唱会", "新", 500_000L, false, "url"));

        assertThat(result.notifyReasons()).containsExactly(MatchEventService.REASON_NEW_EVENT);
        assertThat(result.event().getObservedCount()).isEqualTo(1);
        assertThat(result.event().getFirstRank()).isEqualTo(5);
        assertThat(result.event().getBestRank()).isEqualTo(5);
        assertThat(result.event().getNormalizedKeyword()).isEqualTo("周杰伦演唱会");
        assertThat(result.event().getDeliveryStatus()).isEqualTo(MatchEvent.DELIVERY_NONE);
    }

    @Test
    void repeatObservationInWindowUpdatesWithoutNotify() {
        mockExisting(activeEvent("话题"));
        mockSavePassthrough();

        var result = service.record(subscription(),
                new HotSearchItem(16, "话题", "热", 95_000L, false, "url"));

        assertThat(result.notifyReasons()).isEmpty();
        assertThat(result.event().getObservedCount()).isEqualTo(4);
        assertThat(result.event().getLatestRank()).isEqualTo(16);
        assertThat(result.event().getBestRank()).isEqualTo(15);
        assertThat(result.event().getMaxHotValue()).isEqualTo(100_000L);
    }

    @Test
    void labelUpgradeToBoomTriggersNotify() {
        mockExisting(activeEvent("话题"));
        mockSavePassthrough();

        var result = service.record(subscription(),
                new HotSearchItem(16, "话题", "爆", 95_000L, false, "url"));

        assertThat(result.notifyReasons()).containsExactly(MatchEventService.REASON_LABEL_UPGRADE);
    }

    @Test
    void rankEnteringTopTriggersNotify() {
        mockExisting(activeEvent("话题"));
        mockSavePassthrough();

        var result = service.record(subscription(),
                new HotSearchItem(2, "话题", "热", 95_000L, false, "url"));

        assertThat(result.notifyReasons()).containsExactly(MatchEventService.REASON_RANK_TOP);
        assertThat(result.event().getBestRank()).isEqualTo(2);
    }

    @Test
    void hotSurgeTriggersNotify() {
        mockExisting(activeEvent("话题"));
        mockSavePassthrough();

        var result = service.record(subscription(),
                new HotSearchItem(16, "话题", "热", 250_000L, false, "url"));

        assertThat(result.notifyReasons()).containsExactly(MatchEventService.REASON_HOT_SURGE);
        assertThat(result.event().getMaxHotValue()).isEqualTo(250_000L);
    }

    @Test
    void hitOutsideActiveWindowCreatesNewEvent() {
        MatchEvent stale = activeEvent("话题");
        stale.setLastSeenAt(LocalDateTime.now().minusHours(7));
        mockExisting(stale);
        mockSavePassthrough();

        var result = service.record(subscription(),
                new HotSearchItem(8, "话题", "热", 80_000L, false, "url"));

        assertThat(result.notifyReasons()).containsExactly(MatchEventService.REASON_NEW_EVENT);
        assertThat(result.event().getId()).isNull();
        assertThat(result.event().getObservedCount()).isEqualTo(1);
    }
}
