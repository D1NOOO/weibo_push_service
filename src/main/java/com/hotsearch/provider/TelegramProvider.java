package com.hotsearch.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component("telegram")
public class TelegramProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(TelegramProvider.class);
    private final ObjectMapper objectMapper;

    public TelegramProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(Channel channel, HotSearchItem primaryItem, List<HotSearchItem> allItems) {
        Map<String, Object> config = channel.getConfigMap();
        String token = (String) config.get("token");
        String chatId = (String) config.get("chatId");
        
        // Fallback: try extracting from webhookUrl
        if (token == null || token.isBlank()) {
            String webhookUrl = (String) config.get("webhookUrl");
            if (webhookUrl != null && webhookUrl.contains("/bot")) {
                String[] parts = webhookUrl.split("/bot", 2);
                if (parts.length > 1) {
                    String[] tokenAndRest = parts[1].split("/", 2);
                    token = tokenAndRest[0];
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
            throw new RuntimeException("Telegram Bot Token 或 Chat ID 未配置");
        }

        try {
            StringBuilder text = new StringBuilder();
            text.append("🔥 *微博热搜提醒*\n\n");
            text.append("*").append(escapeMarkdown(primaryItem.keyword())).append("*");
            if (primaryItem.label() != null) text.append(" [").append(primaryItem.label()).append("]");
            if (primaryItem.hotValue() != null) text.append(" — 热度 ").append(formatHeat(primaryItem.hotValue()));
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

            String apiUrl = "https://api.telegram.org/bot" + token + "/sendMessage";
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text.toString());
            body.put("parse_mode", "Markdown");

            String json = objectMapper.writeValueAsString(body);
            String resp = Jsoup.connect(apiUrl)
                    .requestBody(json)
                    .header("Content-Type", "application/json")
                    .ignoreContentType(true)
                    .post()
                    .body().text();

            Map<String, Object> respMap = objectMapper.readValue(resp, Map.class);
            if (!Boolean.TRUE.equals(respMap.get("ok"))) {
                String desc = respMap.get("description") != null ? respMap.get("description").toString() : "未知错误";
                throw new RuntimeException("Telegram返回错误: " + desc);
            }

            log.info("Telegram推送成功: keyword={}", primaryItem.keyword());
        } catch (Exception e) {
            throw new RuntimeException("Telegram推送失败: " + e.getMessage(), e);
        }
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_").replace("*", "\\*").replace("[", "\\[");
    }

    private String formatHeat(long hotValue) {
        if (hotValue >= 10000) return String.format("%.1f万", hotValue / 10000.0);
        return String.valueOf(hotValue);
    }
}
