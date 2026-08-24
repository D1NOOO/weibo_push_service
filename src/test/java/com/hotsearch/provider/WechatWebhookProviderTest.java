package com.hotsearch.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatWebhookProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProviderHttpClient httpClient = mock(ProviderHttpClient.class);
    private final WechatWebhookProvider provider = new WechatWebhookProvider(httpClient);

    @Test
    void parsesAndDeduplicatesConfiguredWxIds() {
        Channel channel = channel(Map.of("wxIdList", "filehelper, wxid_a，group@chatroom\nfilehelper"));

        assertThat(provider.getTargets(channel))
                .containsExactly("filehelper", "wxid_a", "group@chatroom");
    }

    @Test
    void supportsWxIdListConfiguredAsJsonArray() {
        Channel channel = channel(Map.of("wxIdList", List.of("filehelper", "wxid_a")));

        assertThat(provider.getTargets(channel)).containsExactly("filehelper", "wxid_a");
    }

    @Test
    void sendsGatewayPayloadWithDefaultTokenHeader() {
        Channel channel = channel(Map.of(
                "webhookUrl", "http://127.0.0.1:19099/webhooks/v1/weibo-hotsearch",
                "token", "secret-token",
                "wxIdList", "filehelper"
        ));
        channel.setId(12L);
        when(httpClient.postJson(anyString(), any(), anyMap()))
                .thenReturn(node("{\"success\":true,\"data\":{\"duplicate\":false}}"));

        provider.send(new PushMessage(channel, List.of(item()), "filehelper", "规则A"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).postJson(
                eq("http://127.0.0.1:19099/webhooks/v1/weibo-hotsearch"),
                bodyCaptor.capture(),
                eq(Map.of("X-Webhook-Token", "secret-token")));
        Map<String, Object> body = bodyCaptor.getValue();
        assertThat(body)
                .containsEntry("type", "weibo.hotsearch.matched")
                .containsEntry("wx_id_list", List.of("filehelper"));
        assertThat(body.get("id")).asString().startsWith("weibo-hotsearch-");
        assertThat(body.get("message")).asString()
                .contains("规则A", "测试热搜", "https://example.com");
    }

    @Test
    void prefixesBearerForAuthorizationHeader() {
        Channel channel = channel(Map.of(
                "webhookUrl", "https://gateway.example/webhooks/v1/source",
                "token", "secret-token",
                "tokenHeader", "Authorization",
                "wxIdList", "filehelper"
        ));
        when(httpClient.postJson(anyString(), any(), anyMap()))
                .thenReturn(node("{\"success\":true}"));

        provider.send(new PushMessage(channel, List.of(item()), "filehelper", null));

        verify(httpClient).postJson(anyString(), any(),
                eq(Map.of("Authorization", "Bearer secret-token")));
    }

    @Test
    void acceptsDuplicateResponseAsSuccessfulDelivery() {
        Channel channel = configuredChannel();
        when(httpClient.postJson(anyString(), any(), anyMap()))
                .thenReturn(node("{\"success\":true,\"data\":{\"duplicate\":true,\"notificationQueued\":false}}"));

        provider.send(new PushMessage(channel, List.of(item()), "filehelper", null));

        verify(httpClient).postJson(anyString(), any(), anyMap());
    }

    @Test
    void gatewayBusinessErrorThrowsProviderExceptionWithTraceId() {
        Channel channel = configuredChannel();
        when(httpClient.postJson(anyString(), any(), anyMap()))
                .thenReturn(node("{\"success\":false,\"code\":\"INVALID_ARGUMENT\",\"message\":\"bad wx id\",\"traceId\":\"trace-1\"}"));

        assertThatThrownBy(() -> provider.send(
                new PushMessage(channel, List.of(item()), "filehelper", null)))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("INVALID_ARGUMENT")
                .hasMessageContaining("trace-1");
    }

    @Test
    void missingRequiredConfigurationFailsBeforeHttpCall() {
        Channel channel = channel(Map.of("wxIdList", "filehelper"));

        assertThatThrownBy(() -> provider.send(
                new PushMessage(channel, List.of(item()), "filehelper", null)))
                .isInstanceOf(ProviderConfigException.class)
                .hasMessageContaining("地址未配置");
    }

    private static Channel configuredChannel() {
        return channel(Map.of(
                "webhookUrl", "https://gateway.example/webhooks/v1/source",
                "token", "secret-token",
                "wxIdList", "filehelper"
        ));
    }

    private static Channel channel(Map<String, Object> config) {
        Channel channel = new Channel();
        channel.setProvider("wechatWebhook");
        channel.setConfigMap(config);
        return channel;
    }

    private static HotSearchItem item() {
        return new HotSearchItem(1, "测试热搜", "热", 99999L, false, "https://example.com");
    }

    private static ObjectNode node(String json) {
        try {
            return (ObjectNode) MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
