package com.hotsearch.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 钉钉群机器人 webhook，text 消息。通道配置：webhookUrl。 */
@Component("dingtalk")
public class DingtalkProvider implements MessageProvider {

    private static final Logger log = LoggerFactory.getLogger(DingtalkProvider.class);

    private final ProviderHttpClient httpClient;

    public DingtalkProvider(ProviderHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void send(PushMessage message) {
        Map<String, Object> config = message.channel().getConfigMap();
        String webhookUrl = (String) config.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new ProviderConfigException("钉钉webhook地址未配置");
        }

        Map<String, Object> body = Map.of(
                "msgtype", "text",
                "text", Map.of("content", buildText(message)));

        JsonNode resp = httpClient.postJson(webhookUrl, body);
        int errcode = resp.path("errcode").asInt(0);
        if (errcode != 0) {
            String errmsg = resp.path("errmsg").asText("未知错误");
            throw new ProviderException("钉钉返回错误 errcode=" + errcode + ": " + errmsg);
        }
        log.info("钉钉推送成功: keyword={}", message.primaryItem().keyword());
    }

    private String buildText(PushMessage message) {
        HotSearchItem primaryItem = message.primaryItem();
        List<HotSearchItem> allItems = message.items();

        StringBuilder text = new StringBuilder();
        text.append(MessageFormats.normalizeTitle(message.title())).append("\n\n");
        text.append("【").append(primaryItem.keyword()).append("】");
        if (primaryItem.label() != null) text.append(" [").append(primaryItem.label()).append("]");
        if (primaryItem.hotValue() != null) {
            text.append(" — 热度 ").append(MessageFormats.formatHeat(primaryItem.hotValue()));
        }
        text.append("\n排名: #").append(primaryItem.rank());
        if (primaryItem.isAd()) text.append(" (广告)");

        if (allItems.size() > 1) {
            text.append("\n\n其他匹配项：");
            for (int i = 1; i < allItems.size() && i < 5; i++) {
                HotSearchItem item = allItems.get(i);
                text.append("\n- ").append(item.keyword());
                if (item.label() != null) text.append(" [").append(item.label()).append("]");
            }
        }
        return text.toString();
    }
}
