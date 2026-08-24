package com.hotsearch.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 通过本地微信 HTTP API（见 wx_RestfulAPI.md）向指定聊天发送文本消息。
 * 通道配置：apiBaseUrl（默认 http://localhost:5001）、token、chat（逗号分隔的多个聊天名）。
 */
@Component("wechat")
public class WechatProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(WechatProvider.class);
    private static final String DEFAULT_BASE_URL = "http://localhost:5001";

    private final ProviderHttpClient httpClient;

    public WechatProvider(ProviderHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public List<String> getTargets(Channel channel) {
        Map<String, Object> config = channel.getConfigMap();
        String chat = (String) config.get("chat");
        if (chat == null || chat.isBlank()) return List.of();
        return Arrays.stream(chat.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public void send(PushMessage message) {
        Map<String, Object> config = message.channel().getConfigMap();
        String apiBaseUrl = (String) config.getOrDefault("apiBaseUrl", DEFAULT_BASE_URL);
        String token = (String) config.get("token");
        String target = message.target();

        if (token == null || token.isBlank()) {
            throw new ProviderConfigException("微信API token未配置");
        }
        if (target == null || target.isBlank()) {
            throw new ProviderConfigException("微信目标聊天名称未配置");
        }

        String url = apiBaseUrl.replaceAll("/$", "") + "/api/send/message";
        Map<String, Object> body = Map.of(
                "chat", target,
                "message", buildMessage(message.items(), message.title()));

        JsonNode resp = httpClient.postJson(url, body, Map.of("Authorization", "Bearer " + token));
        if (!resp.path("success").asBoolean(false)) {
            String error = resp.path("message").asText("未知错误");
            if (isRateLimitError(error)) {
                throw new RateLimitedException("微信限频(chat=" + target + "): " + error);
            }
            throw new ProviderException("微信返回错误(chat=" + target + "): " + error);
        }
        log.info("微信推送成功: chat={}, keyword={}", target, message.primaryItem().keyword());
    }

    /** 本地微信 API 的限频响应：错误码 11232 或 frequency limited 文案。 */
    private static boolean isRateLimitError(String error) {
        if (error == null) return false;
        String lower = error.toLowerCase();
        return lower.contains("11232") || lower.contains("frequency limited")
                || lower.contains("frequencylimited") || lower.contains("rate limit");
    }

    String buildMessage(List<HotSearchItem> allItems, String messageTitle) {
        return MessageFormats.buildPlainText(allItems, messageTitle);
    }
}
