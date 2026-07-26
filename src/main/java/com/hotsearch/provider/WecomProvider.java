package com.hotsearch.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 企业微信群机器人 webhook，markdown 消息。通道配置：webhookUrl。 */
@Component("wecom")
public class WecomProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(WecomProvider.class);

    private final ProviderHttpClient httpClient;

    public WecomProvider(ProviderHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void send(PushMessage message) {
        Map<String, Object> config = message.channel().getConfigMap();
        String webhookUrl = (String) config.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new ProviderConfigException("企微webhook地址未配置");
        }

        Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("content", buildMarkdown(message)));

        JsonNode resp = httpClient.postJson(webhookUrl, body);
        int errcode = resp.path("errcode").asInt(0);
        if (errcode != 0) {
            String errmsg = resp.path("errmsg").asText("未知错误");
            throw new ProviderException("企微返回错误 errcode=" + errcode + ": " + errmsg);
        }
        log.info("企微推送成功: keyword={}", message.primaryItem().keyword());
    }

    private String buildMarkdown(PushMessage message) {
        HotSearchItem primaryItem = message.primaryItem();
        List<HotSearchItem> allItems = message.items();

        StringBuilder md = new StringBuilder();
        md.append("**").append(MessageFormats.normalizeTitle(message.title())).append("**\n\n");
        md.append("> **").append(primaryItem.keyword()).append("**");
        if (primaryItem.label() != null) md.append(" [").append(primaryItem.label()).append("]");
        if (primaryItem.hotValue() != null) {
            md.append(" — 热度 ").append(MessageFormats.formatHeat(primaryItem.hotValue()));
        }
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
        return md.toString();
    }
}
