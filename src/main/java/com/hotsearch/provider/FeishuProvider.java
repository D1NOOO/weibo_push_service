package com.hotsearch.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书推送，支持两种模式（通道配置 mode）：
 * - webhook（默认）：群自定义机器人，配置 webhookUrl
 * - app：企业自建应用，配置 appId/appSecret/receiveId/receiveIdType
 */
@Component("feishu")
public class FeishuProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(FeishuProvider.class);
    private static final String TENANT_TOKEN_URL =
            "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String SEND_MESSAGE_URL =
            "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=";

    private final ProviderHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FeishuProvider(ProviderHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(PushMessage message) {
        Map<String, Object> config = message.channel().getConfigMap();
        String mode = (String) config.getOrDefault("mode", "webhook");
        if ("app".equals(mode)) {
            sendViaApp(config, message);
            return;
        }
        sendViaWebhook(config, message);
    }

    private void sendViaWebhook(Map<String, Object> config, PushMessage message) {
        String webhookUrl = (String) config.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new ProviderConfigException("飞书webhook地址未配置");
        }

        Map<String, Object> body = Map.of(
                "msg_type", "interactive",
                "card", buildCardContent(message));

        JsonNode resp = httpClient.postJson(webhookUrl, body);
        requireZeroCode(resp, "飞书返回错误");
        log.info("飞书推送成功: keyword={}", message.primaryItem().keyword());
    }

    private void sendViaApp(Map<String, Object> config, PushMessage message) {
        String appId = getString(config, "appId", "app_id");
        String appSecret = getString(config, "appSecret", "app_secret");
        String receiveId = getString(config, "receiveId", "token");
        String receiveIdType = getString(config, "receiveIdType");
        if (receiveIdType == null || receiveIdType.isBlank()) receiveIdType = "chat_id";

        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new ProviderConfigException("飞书 App ID 或 App Secret 未配置");
        }
        if (receiveId == null || receiveId.isBlank()) {
            throw new ProviderConfigException("飞书接收ID未配置");
        }

        String accessToken = getTenantAccessToken(appId, appSecret);
        String cardJson;
        try {
            cardJson = objectMapper.writeValueAsString(buildCardContent(message));
        } catch (Exception e) {
            throw new ProviderException("飞书卡片序列化失败: " + e.getMessage(), e);
        }
        Map<String, Object> body = Map.of(
                "receive_id", receiveId,
                "msg_type", "interactive",
                "content", cardJson);

        JsonNode resp = httpClient.postJson(SEND_MESSAGE_URL + receiveIdType, body,
                Map.of("Authorization", "Bearer " + accessToken));
        requireZeroCode(resp, "飞书应用消息返回错误");
        log.info("飞书应用消息推送成功: keyword={}", message.primaryItem().keyword());
    }

    private String getTenantAccessToken(String appId, String appSecret) {
        JsonNode resp = httpClient.postJson(TENANT_TOKEN_URL,
                Map.of("app_id", appId, "app_secret", appSecret));
        requireZeroCode(resp, "获取飞书 tenant_access_token 失败");
        String token = resp.path("tenant_access_token").asText("");
        if (token.isBlank()) {
            throw new ProviderException("飞书 tenant_access_token 为空");
        }
        return token;
    }

    private void requireZeroCode(JsonNode resp, String errorPrefix) {
        int code = resp.path("code").asInt(0);
        if (code != 0) {
            String msg = resp.path("msg").asText("未知错误");
            throw new ProviderException(errorPrefix + " code=" + code + ": " + msg);
        }
    }

    private String getString(Map<String, Object> config, String... keys) {
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null) return value.toString();
        }
        return null;
    }

    private Map<String, Object> buildCardContent(PushMessage message) {
        List<HotSearchItem> allItems = message.items();

        Map<String, Object> cardContent = new HashMap<>();
        cardContent.put("header", Map.of(
                "title", Map.of("content", MessageFormats.normalizeTitle(message.title()), "tag", "plain_text"),
                "template", "red"
        ));

        List<Map<String, Object>> elements = new ArrayList<>();

        // 带可点击链接的排行列表
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < allItems.size(); i++) {
            HotSearchItem item = allItems.get(i);
            sb.append(i + 1).append(". ");
            if (item.url() != null && !item.url().isBlank()) {
                sb.append("[").append(escapeMd(item.keyword())).append("](").append(item.url()).append(")");
            } else {
                sb.append(escapeMd(item.keyword()));
            }
            if (item.label() != null) {
                sb.append(" 「").append(item.label()).append("」");
            }
            if (item.hotValue() != null) {
                sb.append(" · ").append(MessageFormats.formatHeat(item.hotValue()));
            }
            if (item.isAd()) {
                sb.append(" ⚠️广告");
            }
            sb.append("\n");
        }

        elements.add(Map.of("tag", "div", "text", Map.of("content", sb.toString(), "tag", "lark_md")));
        elements.add(Map.of("tag", "note", "elements", List.of(
                Map.of("tag", "plain_text",
                        "content", "⏱ " + MessageFormats.shortDisplayTime() + " · 非实时数据，仅供参考")
        )));

        cardContent.put("elements", elements);
        return cardContent;
    }

    private String escapeMd(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("*", "\\*")
                   .replace("_", "\\_")
                   .replace("~", "\\~")
                   .replace("`", "\\`")
                   .replace("[", "\\[")
                   .replace("]", "\\]")
                   .replace(">", "\\>");
    }
}
