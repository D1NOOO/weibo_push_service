package com.hotsearch.service;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import com.hotsearch.entity.Subscription;
import com.hotsearch.matcher.SubscriptionMatcher;
import com.hotsearch.matcher.SubscriptionMatcher.MatchResult;
import com.hotsearch.service.DeliveryPlanner.SubscriptionDelivery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineServiceTest {

    private final HotSearchService hotSearchService = mock(HotSearchService.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final ChannelService channelService = mock(ChannelService.class);
    private final SubscriptionMatcher matcher = mock(SubscriptionMatcher.class);
    private final DeliveryPlanner planner = mock(DeliveryPlanner.class);
    private final DeliveryExecutor executor = mock(DeliveryExecutor.class);
    private final ApplicationConfigService configService = mock(ApplicationConfigService.class);

    private PipelineService service(ExecutorService pipelineExecutor) {
        return new PipelineService(hotSearchService, subscriptionService, channelService,
                matcher, planner, executor, configService, pipelineExecutor);
    }

    @Test
    void skipsMatchingWhenNoItemsFetched() {
        when(hotSearchService.fetchAndSave()).thenReturn(List.of());

        assertThat(service(new DirectExecutorService()).trigger()).isTrue();

        verify(matcher, never()).match(anyList(), anyList());
    }

    @Test
    void skipsPlanningWhenNothingMatches() {
        when(hotSearchService.fetchAndSave()).thenReturn(List.of(item()));
        when(subscriptionService.listAllEnabled()).thenReturn(List.of());
        when(matcher.match(anyList(), anyList())).thenReturn(List.of());

        service(new DirectExecutorService()).trigger();

        verify(planner, never()).plan(anyList(), anyList(), any());
    }

    @Test
    void executesEveryPlannedDelivery() {
        Subscription sub = new Subscription();
        sub.setId(1L);
        sub.setUserId(10L);
        Channel channel = new Channel();
        channel.setId(100L);
        channel.setUserId(10L);
        MatchResult match = new MatchResult(sub, item());
        SubscriptionDelivery first = new SubscriptionDelivery(sub, channel, List.of(match));
        SubscriptionDelivery second = new SubscriptionDelivery(sub, channel, List.of(match));

        when(hotSearchService.fetchAndSave()).thenReturn(List.of(item()));
        when(subscriptionService.listAllEnabled()).thenReturn(List.of(sub));
        when(matcher.match(anyList(), anyList())).thenReturn(List.of(match));
        when(channelService.listAllEnabled()).thenReturn(List.of(channel));
        when(configService.getDedupeWindowHours()).thenReturn(6);
        when(planner.plan(anyList(), anyList(), any())).thenReturn(List.of(first, second));

        service(new DirectExecutorService()).trigger();

        verify(executor, times(2)).execute(any(), any());
    }

    @Test
    void reportsBusyWhileAPreviousRunIsStillActive() {
        // 任务被吞掉不执行 => running 标志保持占用
        PipelineService service = service(new SwallowingExecutorService());

        assertThat(service.trigger()).isTrue();
        assertThat(service.trigger()).isFalse();
    }

    private static HotSearchItem item() {
        return new HotSearchItem(1, "关键词", "热", 1000L, false, "");
    }

    /** 在调用线程上同步执行任务，便于断言。 */
    private static class DirectExecutorService extends AbstractExecutorService {
        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() {}
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    /** 接收任务但从不执行，用于模拟“上一轮仍在运行”。 */
    private static class SwallowingExecutorService extends AbstractExecutorService {
        @Override public void execute(Runnable command) { /* 不执行 */ }
        @Override public void shutdown() {}
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }
}
