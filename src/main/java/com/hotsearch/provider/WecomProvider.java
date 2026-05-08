package com.hotsearch.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component("wecom")
public class WecomProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(WecomProvider.class);
    private final ObjectMapper objectMapper;

    public WecomProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(Channel channel, HotSearchItem primaryItem, List<HotSearchItem> allItems) {
        Map<String, Object> config = channel.getConfigMap();
        String webhookUrl = (String) config.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new RuntimeException("企微webhook地址未配置");
        }

        try {
            StringBuilder md = new StringBuilder();
            md.append("🔥 **微博热搜提醒**\n\n");
            md.append("> **").append(primaryItem.keyword()).append("**");
            if (primaryItem.label() != null) md.append(" [").append(primaryItem.label()).append("]");
            if (primaryItem.hotValue() != null) md.append(" — 热度 ").append(formatHeat(primaryItem.hotValue()));
            md.append("\n> 排名: #").append(primaryItem.rank());
            if (primaryItem.isAd()) md.append(" (广告)");
            
            if (allItems.size() > 1) {
                md.append("\n\n**其他匹配项：**");
                for (int i = 1; i < allItems.size() && i < 5; i++) {
                    HotSearchItem item = allItems.get(i);
                    md.append("\n> - ").append(item.keyword());
                    if (item.label() != null) md.append(" [").append(item.label()).append("]");
                }
            }

            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", Map.of("content", md.toString()));

            String json = objectMapper.writeValueAsString(body);
            Jsoup.connect(webhookUrl)
                    .requestBody(json)
                    .header("Content-Type", "application/json")
                    .ignoreContentType(true)
                    .post();

            log.info("企微推送成功: keyword={}", primaryItem.keyword());
        } catch (Exception e) {
            throw new RuntimeException("企微推送失败: " + e.getMessage(), e);
        }
    }

    private String formatHeat(long hotValue) {
        if (hotValue >= 10000) return String.format("%.1f万", hotValue / 10000.0);
        return String.valueOf(hotValue);
    }
}
