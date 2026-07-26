package com.hotsearch.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.junit.jupiter.api.Test;

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

class WechatProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProviderHttpClient httpClient = mock(ProviderHttpClient.class);
    private final WechatProvider provider = new WechatProvider(httpClient);

    @Test
    void rendersUrlsPreparedByTheDeliveryPipeline() {
        HotSearchItem item = new HotSearchItem(1, "世界杯", "热", 1_998_000L, false,
                "https://s.example.com/abcde");

        String message = provider.buildMessage(List.of(item), "世界杯热搜提醒");

        assertThat(message).contains("🔗https://s.example.com/abcde");
    }

    @Test
    void parsesCommaSeparatedTargets() {
        Channel channel = channel(Map.of("chat", "家人群, 工作群，好友A"));

        assertThat(provider.getTargets(channel)).containsExactly("家人群", "工作群", "好友A");
    }

    @Test
    void returnsEmptyTargetsWhenChatMissing() {
        assertThat(provider.getTargets(channel(Map.of()))).isEmpty();
    }

    @Test
    void sendsAuthorizedRequestAndSucceeds() {
        Channel channel = channel(Map.of("token", "secret-token", "chat", "家人群"));
        when(httpClient.postJson(anyString(), any(), anyMap())).thenReturn(node("{\"success\":true}"));

        provider.send(new PushMessage(channel, List.of(item()), "家人群", "规则A"));

        verify(httpClient).postJson(
                eq("http://localhost:5001/api/send/message"),
                any(),
                eq(Map.of("Authorization", "Bearer secret-token")));
    }

    @Test
    void missingTokenThrowsConfigException() {
        Channel channel = channel(Map.of("chat", "家人群"));

        assertThatThrownBy(() -> provider.send(new PushMessage(channel, List.of(item()), "家人群", null)))
                .isInstanceOf(ProviderConfigException.class)
                .hasMessageContaining("token未配置");
    }

    @Test
    void rateLimitResponseThrowsRateLimitedException() {
        Channel channel = channel(Map.of("token", "t", "chat", "家人群"));
        when(httpClient.postJson(anyString(), any(), anyMap()))
                .thenReturn(node("{\"success\":false,\"message\":\"error 11232: frequency limited\"}"));

        assertThatThrownBy(() -> provider.send(new PushMessage(channel, List.of(item()), "家人群", null)))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void errorResponseThrowsProviderException() {
        Channel channel = channel(Map.of("token", "t", "chat", "家人群"));
        when(httpClient.postJson(anyString(), any(), anyMap()))
                .thenReturn(node("{\"success\":false,\"message\":\"chat not found\"}"));

        assertThatThrownBy(() -> provider.send(new PushMessage(channel, List.of(item()), "家人群", null)))
                .isInstanceOf(ProviderException.class)
                .isNotInstanceOf(RateLimitedException.class)
                .hasMessageContaining("chat not found");
    }

    private static Channel channel(Map<String, Object> config) {
        Channel channel = new Channel();
        channel.setProvider("wechat");
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
