package com.hotsearch.service;

import com.hotsearch.config.WxProperties;
import com.hotsearch.entity.Channel;
import com.hotsearch.entity.DeliveryLog;
import com.hotsearch.entity.MatchEvent;
import com.hotsearch.entity.Subscription;
import com.hotsearch.entity.User;
import com.hotsearch.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 微信小程序订阅消息通知编排：额度校验 -> 组装模板数据 -> 云函数发送 -> 回写事件与日志。
 * 只在命中事件产生通知原因（首次命中/标签升级/进前排/热度激增）时调用，保护稀缺的一次性授权额度。
 */
@Service
public class WxNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WxNotificationService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WxProperties wxProperties;
    private final WxCloudFunctionClient cloudFunctionClient;
    private final NotificationQuotaService quotaService;
    private final MatchEventService matchEventService;
    private final UserRepository userRepository;
    private final DeliveryService deliveryService;
    private final ZoneId displayZone;

    public WxNotificationService(WxProperties wxProperties, WxCloudFunctionClient cloudFunctionClient,
                                 NotificationQuotaService quotaService, MatchEventService matchEventService,
                                 UserRepository userRepository, DeliveryService deliveryService) {
        this.wxProperties = wxProperties;
        this.cloudFunctionClient = cloudFunctionClient;
        this.quotaService = quotaService;
        this.matchEventService = matchEventService;
        this.userRepository = userRepository;
        this.deliveryService = deliveryService;
        this.displayZone = ZoneId.of("Asia/Shanghai");
    }

    /**
     * 对某订阅本轮所有需要通知的命中事件执行微信通知。
     * channels 为全部启用通道；内部筛选归属该用户、被订阅绑定的 wxsubscribe 通道。
     */
    public void notifyForSubscription(Subscription sub, List<Channel> channels,
                                      List<MatchEventService.RecordResult> recorded) {
        List<MatchEventService.RecordResult> toNotify = recorded.stream()
                .filter(r -> !r.notifyReasons().isEmpty())
                .toList();
        if (toNotify.isEmpty()) return;

        List<Long> subChannelIds = sub.getChannelIds();
        Channel wxChannel = channels.stream()
                .filter(ch -> ch.getUserId().equals(sub.getUserId()))
                .filter(ch -> NotificationQuotaService.WX_SUBSCRIBE_PROVIDER.equals(ch.getProvider()))
                .filter(ch -> subChannelIds.isEmpty() || subChannelIds.contains(ch.getId()))
                .findFirst().orElse(null);

        for (MatchEventService.RecordResult result : toNotify) {
            String reason = String.join(",", result.notifyReasons());
            if (wxChannel == null) {
                matchEventService.markDelivery(result.event(), MatchEvent.DELIVERY_NO_CHANNEL,
                        reason, "未开启小程序订阅消息通道");
                continue;
            }
            try {
                notifyEvent(sub, wxChannel, result.event(), reason);
            } catch (Exception e) {
                log.error("微信订阅消息通知失败: eventId={}, keyword={}",
                        result.event().getId(), result.event().getKeyword(), e);
                matchEventService.markDelivery(result.event(), MatchEvent.DELIVERY_FAILED, reason, e.getMessage());
            }
        }
    }

    private void notifyEvent(Subscription sub, Channel wxChannel, MatchEvent event, String reason) {
        String templateId = resolveTemplateId(wxChannel);
        if (templateId.isBlank()) {
            matchEventService.markDelivery(event, MatchEvent.DELIVERY_FAILED, reason,
                    "订阅消息模板未配置（WX_SUBSCRIBE_TEMPLATE_ID）");
            return;
        }
        if (!cloudFunctionClient.isConfigured()) {
            matchEventService.markDelivery(event, MatchEvent.DELIVERY_FAILED, reason, "云函数调用未配置");
            return;
        }
        User user = userRepository.findById(sub.getUserId()).orElse(null);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            matchEventService.markDelivery(event, MatchEvent.DELIVERY_FAILED, reason, "用户未绑定微信 openid");
            return;
        }
        if (!quotaService.tryConsume(sub.getUserId(), templateId)) {
            matchEventService.markDelivery(event, MatchEvent.DELIVERY_NO_QUOTA, reason, "订阅消息额度不足");
            saveLog(sub.getUserId(), sub.getId(), wxChannel.getId(), event, "FAILED", "订阅消息额度不足");
            return;
        }

        Map<String, Object> message = buildMessage(user.getOpenid(), templateId, event);
        try {
            WxCloudFunctionClient.InvokeResult invoke = cloudFunctionClient.sendSubscribeMessage(message);
            if (invoke.ok()) {
                matchEventService.markDelivery(event, MatchEvent.DELIVERY_SENT, reason, null);
                saveLog(sub.getUserId(), sub.getId(), wxChannel.getId(), event, "SUCCESS", null);
                log.info("微信订阅消息已发送: userId={}, keyword={}, reason={}",
                        sub.getUserId(), event.getKeyword(), reason);
            } else if (invoke.isQuotaExhausted()) {
                quotaService.reset(sub.getUserId(), templateId);
                matchEventService.markDelivery(event, MatchEvent.DELIVERY_NO_QUOTA, reason,
                        "微信侧无有效订阅授权（43101），本地额度已清零");
                saveLog(sub.getUserId(), sub.getId(), wxChannel.getId(), event, "FAILED", "43101 无有效订阅授权");
            } else {
                quotaService.refund(sub.getUserId(), templateId);
                String error = "errcode=" + invoke.errcode() + ": " + invoke.errmsg();
                matchEventService.markDelivery(event, MatchEvent.DELIVERY_FAILED, reason, error);
                saveLog(sub.getUserId(), sub.getId(), wxChannel.getId(), event, "FAILED", error);
            }
        } catch (Exception e) {
            quotaService.refund(sub.getUserId(), templateId);
            matchEventService.markDelivery(event, MatchEvent.DELIVERY_FAILED, reason, e.getMessage());
            saveLog(sub.getUserId(), sub.getId(), wxChannel.getId(), event, "FAILED", e.getMessage());
        }
    }

    /** 通道测试发送：消耗一次真实额度，直接抛异常反馈失败原因。 */
    public void sendTest(Channel channel) {
        String templateId = resolveTemplateId(channel);
        if (templateId.isBlank()) {
            throw new RuntimeException("订阅消息模板未配置（WX_SUBSCRIBE_TEMPLATE_ID）");
        }
        if (!cloudFunctionClient.isConfigured()) {
            throw new RuntimeException("云函数调用未配置：请设置 WX_CLOUD_SHARED_SECRET 及调用地址/环境");
        }
        User user = userRepository.findById(channel.getUserId()).orElse(null);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            throw new RuntimeException("当前用户未绑定微信 openid，请先在小程序中登录");
        }
        if (!quotaService.tryConsume(channel.getUserId(), templateId)) {
            throw new RuntimeException("订阅消息额度不足，请先在小程序中授权提醒");
        }

        MatchEvent fake = new MatchEvent();
        fake.setKeyword("测试热搜提醒");
        fake.setLatestRank(1);
        fake.setLatestHotValue(99999L);
        fake.setSubscriptionName("测试");
        Map<String, Object> message = buildMessage(user.getOpenid(), templateId, fake);
        try {
            WxCloudFunctionClient.InvokeResult invoke = cloudFunctionClient.sendSubscribeMessage(message);
            if (invoke.ok()) return;
            if (invoke.isQuotaExhausted()) {
                quotaService.reset(channel.getUserId(), templateId);
                throw new RuntimeException("微信侧无有效订阅授权（43101），请重新在小程序中授权");
            }
            quotaService.refund(channel.getUserId(), templateId);
            throw new RuntimeException("发送失败 errcode=" + invoke.errcode() + ": " + invoke.errmsg());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            quotaService.refund(channel.getUserId(), templateId);
            throw new RuntimeException("发送失败：" + e.getMessage());
        }
    }

    private String resolveTemplateId(Channel channel) {
        Object override = channel.getConfigMap().get("templateId");
        if (override != null && !String.valueOf(override).isBlank()) {
            return String.valueOf(override).trim();
        }
        String global = wxProperties.getSubscribe().getTemplateId();
        return global == null ? "" : global.trim();
    }

    private Map<String, Object> buildMessage(String openid, String templateId, MatchEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : wxProperties.getSubscribe().getFieldMapping().entrySet()) {
            String templateKey = entry.getValue();
            if (templateKey == null || templateKey.isBlank()) continue;
            String value = semanticValue(entry.getKey(), event);
            if (value == null) continue;
            data.put(templateKey.trim(), Map.of("value", sanitizeForTemplate(templateKey.trim(), value)));
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("openid", openid);
        message.put("templateId", templateId);
        message.put("page", wxProperties.getSubscribe().getPage());
        message.put("data", data);
        message.put("miniprogramState", wxProperties.getSubscribe().getMiniprogramState());
        message.put("lang", "zh_CN");
        return message;
    }

    private String semanticValue(String semantic, MatchEvent event) {
        return switch (semantic) {
            case "keyword" -> event.getKeyword();
            case "rank" -> event.getLatestRank() == null ? null : "第" + event.getLatestRank() + "名";
            case "hotValue" -> event.getLatestHotValue() == null ? "0" : String.valueOf(event.getLatestHotValue());
            case "time" -> LocalDateTime.now(displayZone).format(TIME_FORMAT);
            case "ruleName" -> event.getSubscriptionName();
            default -> null;
        };
    }

    /** 按微信模板字段类型约束裁剪取值（thing≤20 字、character_string≤32、number 仅数字）。 */
    private String sanitizeForTemplate(String templateKey, String value) {
        String v = value == null ? "" : value.trim();
        if (templateKey.startsWith("thing")) {
            return v.length() <= 20 ? v : v.substring(0, 19) + "…";
        }
        if (templateKey.startsWith("character_string")) {
            return v.length() <= 32 ? v : v.substring(0, 32);
        }
        if (templateKey.startsWith("number")) {
            String digits = v.replaceAll("\\D", "");
            return digits.isBlank() ? "0" : digits;
        }
        if (templateKey.startsWith("phrase")) {
            return v.length() <= 5 ? v : v.substring(0, 5);
        }
        return v;
    }

    private void saveLog(Long userId, Long subscriptionId, Long channelId, MatchEvent event,
                         String status, String error) {
        try {
            DeliveryLog logEntry = new DeliveryLog();
            logEntry.setUserId(userId);
            logEntry.setSubscriptionId(subscriptionId);
            logEntry.setChannelId(channelId);
            logEntry.setKeyword(event.getKeyword());
            logEntry.setLabel(event.getLatestLabel());
            logEntry.setHotValue(event.getLatestHotValue());
            logEntry.setStatus(status);
            logEntry.setError(error);
            logEntry.setBatchId(UUID.randomUUID().toString());
            logEntry.setDeliveredAt(LocalDateTime.now());
            deliveryService.save(logEntry);
        } catch (Exception e) {
            log.warn("写入微信通知日志失败: {}", e.getMessage());
        }
    }
}
