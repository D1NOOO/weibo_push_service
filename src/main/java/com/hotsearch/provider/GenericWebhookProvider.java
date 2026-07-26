package com.hotsearch.provider;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用 Webhook：向任意 HTTP 端点 POST JSON。
 * Payload: { "title": "...", "primaryItem": {...}, "matchedCount": n, "timestamp": "ISO-8601 UTC" }
 */
@Component("generic")
public class GenericWebhookProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(GenericWebhookProvider.class);

    private final ProviderHttpClient httpClient;

    public GenericWebhookProvider(ProviderHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void send(PushMessage message) {
        Map<String, Object> config = message.channel().getConfigMap();
        String webhookUrl = (String) config.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new ProviderConfigException("通用 Webhook 地址未配置");
        }

        HotSearchItem primaryItem = message.primaryItem();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", MessageFormats.normalizeTitle(message.title()));
        payload.put("primaryItem", Map.of(
                "keyword", primaryItem.keyword(),
                "rank", primaryItem.rank(),
                "label", primaryItem.label() != null ? primaryItem.label() : "",
                "hotValue", primaryItem.hotValue() != null ? primaryItem.hotValue() : 0,
                "isAd", primaryItem.isAd(),
                "url", primaryItem.url() != null ? primaryItem.url() : ""
        ));
        payload.put("matchedCount", message.items().size());
        payload.put("timestamp", Instant.now().toString());

        httpClient.postJson(webhookUrl, payload);
        log.info("通用Webhook推送完成: keyword={}", primaryItem.keyword());
    }
}
