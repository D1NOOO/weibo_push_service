package com.hotsearch.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Generic Webhook provider - POSTs JSON to any HTTP endpoint.
 * Payload: { "primaryItem": {...}, "allItems": [...], "timestamp": "..." }
 */
@Component("generic")
public class GenericWebhookProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(GenericWebhookProvider.class);
    private final ObjectMapper objectMapper;

    public GenericWebhookProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(Channel channel, HotSearchItem primaryItem, List<HotSearchItem> allItems) {
        Map<String, Object> config = channel.getConfigMap();
        String webhookUrl = (String) config.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new RuntimeException("通用 Webhook 地址未配置");
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("primaryItem", Map.of(
                    "keyword", primaryItem.keyword(),
                    "rank", primaryItem.rank(),
                    "label", primaryItem.label() != null ? primaryItem.label() : "",
                    "hotValue", primaryItem.hotValue() != null ? primaryItem.hotValue() : 0,
                    "isAd", primaryItem.isAd(),
                    "url", primaryItem.url() != null ? primaryItem.url() : ""
            ));
            payload.put("matchedCount", allItems.size());
            payload.put("timestamp", LocalDateTime.now().toString());

            String json = objectMapper.writeValueAsString(payload);
            var resp = Jsoup.connect(webhookUrl)
                    .requestBody(json)
                    .header("Content-Type", "application/json")
                    .ignoreContentType(true)
                    .execute();

            log.info("通用Webhook推送完成: keyword={}, status={}", primaryItem.keyword(), resp.statusCode());
        } catch (Exception e) {
            throw new RuntimeException("通用Webhook推送失败: " + e.getMessage(), e);
        }
    }
}
