package com.hotsearch.service;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.dto.MatchEventResponse;
import com.hotsearch.entity.MatchEvent;
import com.hotsearch.entity.Subscription;
import com.hotsearch.repository.MatchEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MatchEventService {

    public static final String REASON_NEW_EVENT = "NEW_EVENT";
    public static final String REASON_LABEL_UPGRADE = "LABEL_UPGRADE";
    public static final String REASON_RANK_TOP = "RANK_TOP";
    public static final String REASON_HOT_SURGE = "HOT_SURGE";

    /** 一次命中记录的结果：事件本身 + 触发的通知原因（为空表示不需要通知） */
    public record RecordResult(MatchEvent event, List<String> notifyReasons) {}

    private final MatchEventRepository matchEventRepository;
    private final int activeWindowHours;
    private final int notifyTopRank;
    private final double notifyHotGrowthRatio;
    private final Set<String> notifyLabels;

    public MatchEventService(MatchEventRepository matchEventRepository,
                             @Value("${app.match.active-window-hours:6}") int activeWindowHours,
                             @Value("${app.match.notify-top-rank:3}") int notifyTopRank,
                             @Value("${app.match.notify-hot-growth-ratio:2.0}") double notifyHotGrowthRatio,
                             @Value("${app.match.notify-labels:爆,沸}") String notifyLabels) {
        this.matchEventRepository = matchEventRepository;
        this.activeWindowHours = activeWindowHours;
        this.notifyTopRank = notifyTopRank;
        this.notifyHotGrowthRatio = notifyHotGrowthRatio;
        this.notifyLabels = Arrays.stream(notifyLabels.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * 记录一次订阅命中：活跃窗口内 upsert 同一事件，窗口外新建事件。
     * 返回事件与本次触发的通知原因（降噪：普通重复观测原因为空）。
     */
    @Transactional
    public RecordResult record(Subscription sub, HotSearchItem item) {
        LocalDateTime now = LocalDateTime.now();
        String normalized = normalize(item.keyword());

        Optional<MatchEvent> latest = matchEventRepository
                .findTopByUserIdAndSubscriptionIdAndNormalizedKeywordOrderByLastSeenAtDesc(
                        sub.getUserId(), sub.getId(), normalized);

        List<String> reasons = new ArrayList<>();
        MatchEvent event;

        if (latest.isPresent()
                && latest.get().getLastSeenAt() != null
                && latest.get().getLastSeenAt().isAfter(now.minusHours(activeWindowHours))) {
            event = latest.get();
            String prevLabel = event.getLatestLabel();
            Integer prevBestRank = event.getBestRank();
            Long prevMaxHot = event.getMaxHotValue();

            event.setObservedCount(event.getObservedCount() == null ? 1 : event.getObservedCount() + 1);
            event.setLastSeenAt(now);
            event.setLatestRank(item.rank());
            if (prevBestRank == null || item.rank() < prevBestRank) {
                event.setBestRank(item.rank());
            }
            event.setLatestHotValue(item.hotValue());
            if (item.hotValue() != null && (prevMaxHot == null || item.hotValue() > prevMaxHot)) {
                event.setMaxHotValue(item.hotValue());
            }
            event.setLatestLabel(item.label());
            event.setSubscriptionName(sub.getName());

            if (isNotifyLabel(item.label()) && !isNotifyLabel(prevLabel)) {
                reasons.add(REASON_LABEL_UPGRADE);
            }
            if (item.rank() > 0 && item.rank() <= notifyTopRank
                    && (prevBestRank == null || prevBestRank > notifyTopRank)) {
                reasons.add(REASON_RANK_TOP);
            }
            if (item.hotValue() != null && prevMaxHot != null && prevMaxHot > 0
                    && item.hotValue() >= Math.round(prevMaxHot * notifyHotGrowthRatio)) {
                reasons.add(REASON_HOT_SURGE);
            }
        } else {
            event = new MatchEvent();
            event.setUserId(sub.getUserId());
            event.setSubscriptionId(sub.getId());
            event.setSubscriptionName(sub.getName());
            event.setKeyword(item.keyword());
            event.setNormalizedKeyword(normalized);
            event.setFirstSeenAt(now);
            event.setLastSeenAt(now);
            event.setObservedCount(1);
            event.setFirstRank(item.rank());
            event.setBestRank(item.rank());
            event.setLatestRank(item.rank());
            event.setMaxHotValue(item.hotValue());
            event.setLatestHotValue(item.hotValue());
            event.setLatestLabel(item.label());
            event.setDeliveryStatus(MatchEvent.DELIVERY_NONE);
            reasons.add(REASON_NEW_EVENT);
        }

        return new RecordResult(matchEventRepository.save(event), reasons);
    }

    @Transactional
    public void markDelivery(MatchEvent event, String status, String reason, String error) {
        event.setDeliveryStatus(status);
        if (reason != null) {
            event.setNotifyReason(reason);
        }
        if (MatchEvent.DELIVERY_SENT.equals(status)) {
            event.setNotifiedAt(LocalDateTime.now());
            event.setDeliveryError(null);
        } else {
            event.setDeliveryError(truncate(error, 512));
        }
        matchEventRepository.save(event);
    }

    public MatchEventResponse.Summary listByUser(Long userId, int hours, Long subscriptionId) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<MatchEvent> events = subscriptionId == null
                ? matchEventRepository.findByUserIdAndLastSeenAtAfterOrderByLastSeenAtDesc(userId, since)
                : matchEventRepository.findByUserIdAndSubscriptionIdAndLastSeenAtAfterOrderByLastSeenAtDesc(
                        userId, subscriptionId, since);

        LocalDateTime activeSince = LocalDateTime.now().minusHours(activeWindowHours);
        List<MatchEventResponse> items = events.stream()
                .map(e -> toResponse(e, activeSince))
                .toList();

        long todayNew = matchEventRepository.countByUserIdAndFirstSeenAtAfter(
                userId, LocalDate.now().atStartOfDay());
        long activeCount = items.stream().filter(MatchEventResponse::active).count();
        return new MatchEventResponse.Summary(todayNew, activeCount, items.size(), items);
    }

    private MatchEventResponse toResponse(MatchEvent e, LocalDateTime activeSince) {
        return new MatchEventResponse(e.getId(), e.getSubscriptionId(), e.getSubscriptionName(),
                e.getKeyword(), e.getFirstSeenAt(), e.getLastSeenAt(), e.getObservedCount(),
                e.getFirstRank(), e.getBestRank(), e.getLatestRank(),
                e.getMaxHotValue(), e.getLatestHotValue(), e.getLatestLabel(),
                e.getDeliveryStatus(), e.getNotifyReason(), e.getNotifiedAt(), e.getDeliveryError(),
                e.getLastSeenAt() != null && e.getLastSeenAt().isAfter(activeSince));
    }

    private boolean isNotifyLabel(String label) {
        return label != null && notifyLabels.contains(label.trim());
    }

    private String normalize(String keyword) {
        return keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
