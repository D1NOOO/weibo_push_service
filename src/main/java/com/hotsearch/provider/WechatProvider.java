package com.hotsearch.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component("wechat")
public class WechatProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(WechatProvider.class);
    private static final String DEFAULT_BASE_URL = "http://localhost:5001";
    private final ObjectMapper objectMapper;

    public WechatProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

    /** Send to all configured chats (backward compat, e.g. test button). */
    @Override
    public void send(Channel channel, HotSearchItem primaryItem, List<HotSearchItem> allItems) {
        List<String> targets = getTargets(channel);
        if (targets.isEmpty()) {
            throw new RuntimeException("微信目标聊天名称未配置");
        }
        for (String target : targets) {
            send(channel, primaryItem, allItems, target);
        }
    }

    /** Send to a single specific chat. */
    @Override
    public void send(Channel channel, HotSearchItem primaryItem, List<HotSearchItem> allItems, String target) {
        send(channel, primaryItem, allItems, target, null);
    }

    /** Send to a single specific chat with the subscription rule name as title. */
    @Override
    public void send(Channel channel, HotSearchItem primaryItem, List<HotSearchItem> allItems,
                     String target, String messageTitle) {
        Map<String, Object> config = channel.getConfigMap();
        String apiBaseUrl = (String) config.getOrDefault("apiBaseUrl", DEFAULT_BASE_URL);
        String token = (String) config.get("token");

        if (token == null || token.isBlank()) {
            throw new RuntimeException("微信API token未配置");
        }
        if (target == null || target.isBlank()) {
            throw new RuntimeException("微信目标聊天名称未配置");
        }

        try {
            String text = buildMessage(allItems, messageTitle);

            Map<String, Object> body = new HashMap<>();
            body.put("chat", target);
            body.put("message", text);

            String json = objectMapper.writeValueAsString(body);
            String url = apiBaseUrl.replaceAll("/$", "") + "/api/send/message?token=" + token;

            String resp = Jsoup.connect(url)
                    .requestBody(json)
                    .header("Content-Type", "application/json")
                    .ignoreContentType(true)
                    .post()
                    .body().text();

            Map<String, Object> respMap = objectMapper.readValue(resp, Map.class);
            Object success = respMap.get("success");
            if (!(success instanceof Boolean && (Boolean) success)) {
                String message = respMap.get("message") != null ? respMap.get("message").toString() : "未知错误";
                throw new RuntimeException("微信返回错误: " + message);
            }

            log.info("微信推送成功: chat={}, keyword={}", target, primaryItem.keyword());
        } catch (Exception e) {
            throw new RuntimeException("微信推送失败(chat=" + target + "): " + e.getMessage(), e);
        }
    }

    private String buildMessage(List<HotSearchItem> allItems, String messageTitle) {
        StringBuilder text = new StringBuilder();
        text.append(MessageProvider.normalizeTitle(messageTitle)).append("\n\n");

        for (int i = 0; i < allItems.size(); i++) {
            HotSearchItem item = allItems.get(i);
            text.append(i + 1).append(": ").append(item.keyword());
            if (item.label() != null) text.append(" [").append(item.label()).append("]");
            if (item.hotValue() != null) text.append(" 热度").append(formatHeat(item.hotValue()));
            text.append(" 排名#").append(item.rank());
            if (item.isAd()) text.append(" (广告)");
            if (item.url() != null && !item.url().isBlank()) {
                text.append("\n🔗").append(item.url());
            }
            text.append("\n");
            if (i < allItems.size() - 1) text.append("\n");
        }
        return text.toString();
    }

    private String formatHeat(long hotValue) {
        if (hotValue >= 10000) return String.format("%.1f万", hotValue / 10000.0);
        return String.valueOf(hotValue);
    }
}
