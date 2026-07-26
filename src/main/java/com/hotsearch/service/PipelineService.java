package com.hotsearch.service;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import com.hotsearch.entity.Subscription;
import com.hotsearch.matcher.SubscriptionMatcher;
import com.hotsearch.matcher.SubscriptionMatcher.MatchResult;
import com.hotsearch.service.DeliveryPlanner.SubscriptionDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 热搜推送管线编排：抓取入库 → 订阅匹配 → 规划投递 → 逐任务执行。
 * 管线在专用单线程执行器上运行，调度线程与 HTTP 请求线程只负责触发，不被限频退避阻塞。
 */
@Service
public class PipelineService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final HotSearchService hotSearchService;
    private final SubscriptionService subscriptionService;
    private final ChannelService channelService;
    private final SubscriptionMatcher matcher;
    private final DeliveryPlanner deliveryPlanner;
    private final DeliveryExecutor deliveryExecutor;
    private final ApplicationConfigService configService;
    private final ExecutorService pipelineExecutor;

    @Autowired
    public PipelineService(HotSearchService hotSearchService, SubscriptionService subscriptionService,
                           ChannelService channelService, SubscriptionMatcher matcher,
                           DeliveryPlanner deliveryPlanner, DeliveryExecutor deliveryExecutor,
                           ApplicationConfigService configService) {
        this(hotSearchService, subscriptionService, channelService, matcher,
                deliveryPlanner, deliveryExecutor, configService,
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "hotsearch-pipeline");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    PipelineService(HotSearchService hotSearchService, SubscriptionService subscriptionService,
                    ChannelService channelService, SubscriptionMatcher matcher,
                    DeliveryPlanner deliveryPlanner, DeliveryExecutor deliveryExecutor,
                    ApplicationConfigService configService, ExecutorService pipelineExecutor) {
        this.hotSearchService = hotSearchService;
        this.subscriptionService = subscriptionService;
        this.channelService = channelService;
        this.matcher = matcher;
        this.deliveryPlanner = deliveryPlanner;
        this.deliveryExecutor = deliveryExecutor;
        this.configService = configService;
        this.pipelineExecutor = pipelineExecutor;
    }

    public void scheduledRun() {
        log.info("定时任务触发：开始执行热搜推送管线");
        trigger();
    }

    /**
     * 异步触发管线执行。
     *
     * @return true 表示已提交执行；false 表示上一轮尚未结束，本次跳过
     */
    public boolean trigger() {
        if (!running.compareAndSet(false, true)) {
            log.warn("管线正在执行中，跳过本次触发");
            return false;
        }
        pipelineExecutor.submit(() -> {
            try {
                runPipeline();
            } catch (Exception e) {
                log.error("管线执行异常", e);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    private void runPipeline() {
        List<HotSearchItem> items = hotSearchService.fetchAndSave();
        if (items.isEmpty()) {
            log.info("未获取到热搜数据，跳过");
            return;
        }

        List<Subscription> subs = subscriptionService.listAllEnabled();
        List<MatchResult> matches = matcher.match(items, subs);
        if (matches.isEmpty()) {
            log.info("无匹配结果，跳过推送");
            return;
        }

        List<Channel> channels = channelService.listAllEnabled();
        LocalDateTime utcNow = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime dedupeSince = utcNow.minusHours(configService.getDedupeWindowHours());

        for (SubscriptionDelivery delivery : deliveryPlanner.plan(matches, channels, utcNow)) {
            deliveryExecutor.execute(delivery, dedupeSince);
        }
        log.info("管线执行完成");
    }

    @Override
    public void destroy() {
        pipelineExecutor.shutdownNow();
    }
}
