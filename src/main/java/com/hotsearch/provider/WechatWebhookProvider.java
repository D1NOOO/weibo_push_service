package com.hotsearch.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 通过 Dino WeChat Gateway 的通知入口投递文本消息。
 * 通道配置：webhookUrl、token、tokenHeader（默认 X-Webhook-Token）、wxIdList。
 */
@Component("wechatWebhook")
public class WechatWebhookProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(WechatWebhookProvider.class);
    private static final String DEFAULT_TOKEN_HEADER = "X-Webhook-Token";

    private final ProviderHttpClient httpClient;

    public WechatWebhookProvider(ProviderHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public List<String> getTargets(Channel channel) {
        Object configured = channel.getConfigMap().get("wxIdList");
        if (configured instanceof List<?> values) {
            return distinctTargets(values.stream()
                    .map(value -> value == null ? null : String.valueOf(value))
                    .toList());
        }
        if (!(configured instanceof String value) || value.isBlank()) {
            return List.of();
        }
        return distinctTargets(List.of(value.split("[,，\\r\\n]+")));
    }

    @Override
    public void send(PushMessage message) {
        Map<String, Object> config = message.channel().getConfigMap();
        String webhookUrl = stringConfig(config, "webhookUrl");
        String token = stringConfig(config, "token");
        String tokenHeader = stringConfig(config, "tokenHeader");
        String target = message.target();

        if (webhookUrl == null) {
            throw new ProviderConfigException("微信 Webhook 地址未配置");
        }
        if (token == null) {
            throw new ProviderConfigException("微信 Webhook Token 未配置");
        }
        if (target == null || target.isBlank()) {
            throw new ProviderConfigException("微信 Webhook 接收对象未配置");
        }
        if (tokenHeader == null) tokenHeader = DEFAULT_TOKEN_HEADER;

        String text = MessageFormats.buildPlainText(message.items(), message.title());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", eventId(message));
        payload.put("type", "weibo.hotsearch.matched");
        payload.put("message", text);
        payload.put("wx_id_list", List.of(target));
        payload.put("data", Map.of(
                "matched_count", message.items().size(),
                "primary_keyword", message.primaryItem().keyword()
        ));

        String headerValue = "Authorization".equalsIgnoreCase(tokenHeader)
                && !token.regionMatches(true, 0, "Bearer ", 0, 7)
                ? "Bearer " + token
                : token;
        JsonNode response = httpClient.postJson(webhookUrl, payload, Map.of(tokenHeader, headerValue));
        if (!response.path("success").asBoolean(false)) {
            String code = response.path("code").asText("UNKNOWN_ERROR");
            String error = response.path("message").asText("未知错误");
            String traceId = response.path("traceId").asText("");
            String trace = traceId.isBlank() ? "" : ", traceId=" + traceId;
            throw new ProviderException("微信 Webhook 返回错误: " + code + ": " + error + trace);
        }
        log.info("微信Webhook推送完成: wxId={}, keyword={}, duplicate={}",
                target, message.primaryItem().keyword(), response.path("data").path("duplicate").asBoolean(false));
    }

    private static List<String> distinctTargets(List<String> values) {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String target = value.trim();
            if (!target.isEmpty()) targets.add(target);
        }
        return new ArrayList<>(targets);
    }

    private static String stringConfig(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String eventId(PushMessage message) {
        StringBuilder canonical = new StringBuilder()
                .append(message.channel().getId()).append('\n')
                .append(message.target()).append('\n')
                .append(MessageFormats.normalizeTitle(message.title())).append('\n');
        for (HotSearchItem item : message.items()) {
            canonical.append(item.rank()).append('|')
                    .append(item.keyword()).append('|')
                    .append(item.label()).append('|')
                    .append(item.hotValue()).append('|')
                    .append(item.isAd()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return "weibo-hotsearch-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
