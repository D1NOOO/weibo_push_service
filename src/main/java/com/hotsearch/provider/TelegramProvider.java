package com.hotsearch.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Telegram Bot sendMessage。通道配置：token + chatId（或含 /bot 与 chat_id= 的 webhookUrl，向后兼容）。
 */
@Component("telegram")
public class TelegramProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(TelegramProvider.class);

    private final ProviderHttpClient httpClient;

    public TelegramProvider(ProviderHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void send(PushMessage message) {
        Map<String, Object> config = message.channel().getConfigMap();
        String token = (String) config.get("token");
        String chatId = (String) config.get("chatId");

        // 向后兼容：从旧版 webhookUrl 中提取 token 与 chat_id
        if (token == null || token.isBlank()) {
            String webhookUrl = (String) config.get("webhookUrl");
            if (webhookUrl != null && webhookUrl.contains("/bot")) {
                String[] parts = webhookUrl.split("/bot", 2);
                if (parts.length > 1) {
                    token = parts[1].split("/", 2)[0];
                }
            }
        }
        if (chatId == null && config.get("webhookUrl") != null) {
            String url = (String) config.get("webhookUrl");
            if (url.contains("chat_id=")) {
                chatId = url.substring(url.indexOf("chat_id=") + 8);
            }
        }

        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            throw new ProviderConfigException("Telegram Bot Token 或 Chat ID 未配置");
        }

        String apiUrl = "https://api.telegram.org/bot" + token + "/sendMessage";
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", buildText(message),
                "parse_mode", "Markdown");

        JsonNode resp = httpClient.postJson(apiUrl, body);
        if (!resp.path("ok").asBoolean(false)) {
            String desc = resp.path("description").asText("未知错误");
            throw new ProviderException("Telegram返回错误: " + desc);
        }
        log.info("Telegram推送成功: keyword={}", message.primaryItem().keyword());
    }

    private String buildText(PushMessage message) {
        HotSearchItem primaryItem = message.primaryItem();
        List<HotSearchItem> allItems = message.items();

        StringBuilder text = new StringBuilder();
        text.append("*").append(escapeMarkdown(MessageFormats.normalizeTitle(message.title()))).append("*\n\n");
        text.append("*").append(escapeMarkdown(primaryItem.keyword())).append("*");
        if (primaryItem.label() != null) text.append(" [").append(primaryItem.label()).append("]");
        if (primaryItem.hotValue() != null) {
            text.append(" — 热度 ").append(MessageFormats.formatHeat(primaryItem.hotValue()));
        }
        text.append("\n排名: #").append(primaryItem.rank());
        if (primaryItem.isAd()) text.append(" ⚠️广告");

        if (allItems.size() > 1) {
            text.append("\n\n*其他匹配项：*");
            for (int i = 1; i < allItems.size() && i < 5; i++) {
                HotSearchItem item = allItems.get(i);
                text.append("\n• ").append(escapeMarkdown(item.keyword()));
                if (item.label() != null) text.append(" [").append(item.label()).append("]");
            }
        }
        return text.toString();
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_").replace("*", "\\*").replace("[", "\\[");
    }
}
