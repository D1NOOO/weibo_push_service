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
        String webhookUrl = (String) config.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new RuntimeException("飞书webhook地址未配置");
        }

        try {
            Map<String, Object> card = buildCard(primaryItem, allItems);
            String json = objectMapper.writeValueAsString(card);

            Jsoup.connect(webhookUrl)
                    .requestBody(json)
                    .header("Content-Type", "application/json")
                    .ignoreContentType(true)
                    .post();

            log.info("飞书推送成功: keyword={}", primaryItem.keyword());
        } catch (Exception e) {
            throw new RuntimeException("飞书推送失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildCard(HotSearchItem primaryItem, List<HotSearchItem> allItems) {
        Map<String, Object> card = new HashMap<>();
        card.put("msg_type", "interactive");

        Map<String, Object> content = new HashMap<>();

        Map<String, Object> cardContent = new HashMap<>();
        cardContent.put("header", Map.of(
                "title", Map.of("content", "🔥 微博热搜提醒", "tag", "plain_text"),
                "template", "red"
        ));

        List<Map<String, Object>> elements = new ArrayList<>();

        // Primary item with rich info
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(primaryItem.keyword()).append("**");
        if (primaryItem.label() != null) {
            sb.append(" [").append(primaryItem.label()).append("]");
        }
        if (primaryItem.hotValue() != null) {
            sb.append(" — 热度 ").append(formatHeat(primaryItem.hotValue()));
        }
        sb.append("\n排名: #").append(primaryItem.rank());
        if (primaryItem.isAd()) {
            sb.append(" ⚠️ 广告推广");
        }
        elements.add(Map.of("tag", "div", "text", Map.of("content", sb.toString(), "tag", "lark_md")));

        // Clickable link
        if (primaryItem.url() != null && !primaryItem.url().isBlank()) {
            elements.add(Map.of("tag", "action", "actions", List.of(
                Map.of("tag", "button", "text", Map.of("content", "🔗 查看微博详情", "tag", "plain_text"),
                        "url", primaryItem.url(), "type", "primary")
            )));
        }

        // Other matched items
        if (allItems.size() > 1) {
            StringBuilder otherSb = new StringBuilder("**其他匹配项：**\n");
            for (int i = 1; i < allItems.size() && i < 10; i++) {
                HotSearchItem item = allItems.get(i);
                otherSb.append("- ").append(item.keyword());
                if (item.label() != null) otherSb.append(" [").append(item.label()).append("]");
                if (item.hotValue() != null) otherSb.append(" · ").append(formatHeat(item.hotValue()));
                otherSb.append("\n");
            }
            elements.add(Map.of("tag", "div", "text", Map.of("content", otherSb.toString(), "tag", "lark_md")));
        }

        // Timestamp note
        String timeStr = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        elements.add(Map.of("tag", "note", "elements", List.of(
                Map.of("tag", "plain_text", "content", "⏱ 抓取时间: " + timeStr + " | 数据非实时，仅供参考")
        )));

        cardContent.put("elements", elements);
        content.put("card", cardContent);
        card.put("card", content);

        return card;
    }

    private String formatHeat(long hotValue) {
        if (hotValue >= 10000) return String.format("%.1f万", hotValue / 10000.0);
        return String.valueOf(hotValue);
    }
}
