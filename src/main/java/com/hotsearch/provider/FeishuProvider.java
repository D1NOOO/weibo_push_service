package com.hotsearch.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("feishu")
public class FeishuProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(FeishuProvider.class);
    private final ObjectMapper objectMapper;

    public FeishuProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(Channel channel, HotSearchItem primaryItem, List<HotSearchItem> allItems) {
        Map<String, Object> config = channel.getConfigMap();
        String mode = (String) config.getOrDefault("mode", "webhook");
        if ("app".equals(mode)) {
            sendViaApp(config, primaryItem, allItems);
            return;
        }

        String webhookUrl = (String) config.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new RuntimeException("飞书webhook地址未配置");
        }

        try {
            Map<String, Object> card = buildWebhookCard(primaryItem, allItems);
            String json = objectMapper.writeValueAsString(card);

            String resp = Jsoup.connect(webhookUrl)
                    .requestBody(json)
                    .header("Content-Type", "application/json")
                    .ignoreContentType(true)
                    .post()
                    .body().text();

            Map<String, Object> respMap = objectMapper.readValue(resp, Map.class);
            Object code = respMap.get("code");
            if (code instanceof Number && ((Number) code).intValue() != 0) {
                String msg = respMap.get("msg") != null ? respMap.get("msg").toString() : "未知错误";
                throw new RuntimeException("飞书返回错误 code=" + code + ": " + msg);
            }

            log.info("飞书推送成功: keyword={}", primaryItem.keyword());
        } catch (Exception e) {
            throw new RuntimeException("飞书推送失败: " + e.getMessage(), e);
        }
    }

    private void sendViaApp(Map<String, Object> config, HotSearchItem primaryItem, List<HotSearchItem> allItems) {
        String appId = getString(config, "appId", "app_id");
        String appSecret = getString(config, "appSecret", "app_secret");
        String receiveId = getString(config, "receiveId", "token");
        String receiveIdType = getString(config, "receiveIdType");
        if (receiveIdType == null || receiveIdType.isBlank()) receiveIdType = "chat_id";

        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new RuntimeException("飞书 App ID 或 App Secret 未配置");
        }
        if (receiveId == null || receiveId.isBlank()) {
            throw new RuntimeException("飞书接收ID未配置");
        }

        try {
            String accessToken = getTenantAccessToken(appId, appSecret);
            Map<String, Object> body = new HashMap<>();
            body.put("receive_id", receiveId);
            body.put("msg_type", "interactive");
            body.put("content", objectMapper.writeValueAsString(buildCardContent(primaryItem, allItems)));

            String json = objectMapper.writeValueAsString(body);
            String resp = Jsoup.connect("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + receiveIdType)
                    .requestBody(json)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .ignoreContentType(true)
                    .post()
                    .body().text();

            Map<String, Object> respMap = objectMapper.readValue(resp, Map.class);
            Object code = respMap.get("code");
            if (code instanceof Number && ((Number) code).intValue() != 0) {
                String msg = respMap.get("msg") != null ? respMap.get("msg").toString() : "未知错误";
                throw new RuntimeException("飞书应用消息返回错误 code=" + code + ": " + msg);
            }

            log.info("飞书应用消息推送成功: keyword={}", primaryItem.keyword());
        } catch (Exception e) {
            throw new RuntimeException("飞书应用消息推送失败: " + e.getMessage(), e);
        }
    }

    private String getTenantAccessToken(String appId, String appSecret) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("app_id", appId);
        body.put("app_secret", appSecret);

        String resp = Jsoup.connect("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
                .requestBody(objectMapper.writeValueAsString(body))
                .header("Content-Type", "application/json")
                .ignoreContentType(true)
                .post()
                .body().text();

        Map<String, Object> respMap = objectMapper.readValue(resp, Map.class);
        Object code = respMap.get("code");
        if (code instanceof Number && ((Number) code).intValue() != 0) {
            String msg = respMap.get("msg") != null ? respMap.get("msg").toString() : "未知错误";
            throw new RuntimeException("获取飞书 tenant_access_token 失败 code=" + code + ": " + msg);
        }

        Object token = respMap.get("tenant_access_token");
        if (token == null || token.toString().isBlank()) {
            throw new RuntimeException("飞书 tenant_access_token 为空");
        }
        return token.toString();
    }

    private String getString(Map<String, Object> config, String... keys) {
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null) return value.toString();
        }
        return null;
    }

    private Map<String, Object> buildWebhookCard(HotSearchItem primaryItem, List<HotSearchItem> allItems) {
        Map<String, Object> card = new HashMap<>();
        card.put("msg_type", "interactive");
        card.put("card", buildCardContent(primaryItem, allItems));
        return card;
    }

    private Map<String, Object> buildCardContent(HotSearchItem primaryItem, List<HotSearchItem> allItems) {
        Map<String, Object> cardContent = new HashMap<>();
        cardContent.put("header", Map.of(
                "title", Map.of("content", "🔥 微博热搜提醒", "tag", "plain_text"),
                "template", "red"
        ));

        List<Map<String, Object>> elements = new ArrayList<>();

        // Ranked list with clickable markdown links
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
                sb.append(" · ").append(formatHeat(item.hotValue()));
            }
            if (item.isAd()) {
                sb.append(" ⚠️广告");
            }
            sb.append("\n");
        }

        elements.add(Map.of("tag", "div", "text", Map.of("content", sb.toString(), "tag", "lark_md")));

        // Timestamp note
        String timeStr = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        elements.add(Map.of("tag", "note", "elements", List.of(
                Map.of("tag", "plain_text", "content", "⏱ " + timeStr + " · 非实时数据，仅供参考")
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

    private String formatHeat(long hotValue) {
        if (hotValue >= 10000) return String.format("%.1f万", hotValue / 10000.0);
        return String.valueOf(hotValue);
    }
}
