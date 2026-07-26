package com.hotsearch.service;

import com.hotsearch.config.WxProperties;
import com.hotsearch.entity.Channel;
import com.hotsearch.entity.NotificationQuota;
import com.hotsearch.repository.ChannelRepository;
import com.hotsearch.repository.NotificationQuotaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationQuotaService {

    public static final String WX_SUBSCRIBE_PROVIDER = "wxsubscribe";

    private static final Logger log = LoggerFactory.getLogger(NotificationQuotaService.class);

    private final NotificationQuotaRepository quotaRepository;
    private final ChannelRepository channelRepository;
    private final WxProperties wxProperties;

    public NotificationQuotaService(NotificationQuotaRepository quotaRepository,
                                    ChannelRepository channelRepository,
                                    WxProperties wxProperties) {
        this.quotaRepository = quotaRepository;
        this.channelRepository = channelRepository;
        this.wxProperties = wxProperties;
    }

    /**
     * 小程序 wx.requestSubscribeMessage 授权后回传：每个 accepted 模板 +1 次可发送额度。
     * 首次授权时自动为用户创建 wxsubscribe 通道，保证管线可投递。
     */
    @Transactional
    public Map<String, Object> grant(Long userId, List<String> acceptedTemplateIds) {
        List<String> accepted = acceptedTemplateIds == null ? List.of() : acceptedTemplateIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .toList();
        int granted = 0;
        for (String templateId : accepted) {
            NotificationQuota quota = quotaRepository.findByUserIdAndTemplateId(userId, templateId)
                    .orElseGet(() -> {
                        NotificationQuota q = new NotificationQuota();
                        q.setUserId(userId);
                        q.setTemplateId(templateId);
                        return q;
                    });
            quota.setRemaining(quota.getRemaining() + 1);
            quota.setTotalGranted(quota.getTotalGranted() + 1);
            quota.setUpdatedAt(LocalDateTime.now());
            quotaRepository.save(quota);
            granted++;
        }
        if (granted > 0) {
            ensureWxSubscribeChannel(userId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("granted", granted);
        result.put("quota", quotaSummary(userId));
        return result;
    }

    /** 消耗一次额度；额度不足返回 false。 */
    @Transactional
    public boolean tryConsume(Long userId, String templateId) {
        NotificationQuota quota = quotaRepository.findByUserIdAndTemplateId(userId, templateId).orElse(null);
        if (quota == null || quota.getRemaining() == null || quota.getRemaining() <= 0) {
            return false;
        }
        quota.setRemaining(quota.getRemaining() - 1);
        quota.setUpdatedAt(LocalDateTime.now());
        quotaRepository.save(quota);
        return true;
    }

    /** 发送失败（非额度原因）时回补一次额度。 */
    @Transactional
    public void refund(Long userId, String templateId) {
        quotaRepository.findByUserIdAndTemplateId(userId, templateId).ifPresent(quota -> {
            quota.setRemaining(quota.getRemaining() + 1);
            quota.setUpdatedAt(LocalDateTime.now());
            quotaRepository.save(quota);
        });
    }

    /** 微信侧判定无有效授权（43101）时清零，纠正本地计数漂移。 */
    @Transactional
    public void reset(Long userId, String templateId) {
        quotaRepository.findByUserIdAndTemplateId(userId, templateId).ifPresent(quota -> {
            quota.setRemaining(0);
            quota.setUpdatedAt(LocalDateTime.now());
            quotaRepository.save(quota);
            log.info("订阅消息额度清零（微信返回无有效授权）：userId={}, templateId={}", userId, templateId);
        });
    }

    public Map<String, Object> quotaSummary(Long userId) {
        List<NotificationQuota> rows = quotaRepository.findByUserId(userId);
        List<Map<String, Object>> items = new ArrayList<>();
        int totalRemaining = 0;
        for (NotificationQuota row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("templateId", row.getTemplateId());
            item.put("remaining", row.getRemaining());
            item.put("totalGranted", row.getTotalGranted());
            item.put("updatedAt", row.getUpdatedAt());
            items.add(item);
            totalRemaining += row.getRemaining() == null ? 0 : row.getRemaining();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("templateId", wxProperties.getSubscribe().getTemplateId());
        summary.put("availableCount", totalRemaining);
        summary.put("items", items);
        summary.put("wxChannelReady", channelRepository.findByUserId(userId).stream()
                .anyMatch(ch -> WX_SUBSCRIBE_PROVIDER.equals(ch.getProvider()) && Boolean.TRUE.equals(ch.getEnabled())));
        return summary;
    }

    /** 用户尚无 wxsubscribe 通道时自动创建，已禁用的自动重新启用。 */
    @Transactional
    public Channel ensureWxSubscribeChannel(Long userId) {
        List<Channel> channels = channelRepository.findByUserId(userId);
        Channel existing = channels.stream()
                .filter(ch -> WX_SUBSCRIBE_PROVIDER.equals(ch.getProvider()))
                .findFirst().orElse(null);
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(true);
                existing = channelRepository.save(existing);
            }
            return existing;
        }
        Channel channel = new Channel();
        channel.setUserId(userId);
        channel.setProvider(WX_SUBSCRIBE_PROVIDER);
        channel.setConfigMap(Map.of());
        channel.setEnabled(true);
        log.info("为用户自动创建小程序订阅消息通道：userId={}", userId);
        return channelRepository.save(channel);
    }
}
