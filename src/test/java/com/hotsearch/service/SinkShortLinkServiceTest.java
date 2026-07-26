package com.hotsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通过 mock HttpClient 验证 Sink 短链服务，不依赖本地端口监听，可在受限 CI 环境稳定运行。
 */
class SinkShortLinkServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationConfigService configService = mock(ApplicationConfigService.class);
    private final HttpClient httpClient = mock(HttpClient.class);
    private final SinkShortLinkService service =
            new SinkShortLinkService(objectMapper, configService, httpClient);

    @Test
    void createsShortLinkUsingGlobalSinkConfiguration() throws Exception {
        when(configService.getSinkConfig())
                .thenReturn(new ApplicationConfigService.SinkConfig("https://s.example.com/", "site-token"));
        stubResponse(200, "{\"link\":{\"slug\":\"abcde\"}}");
        String originalUrl = "https://s.weibo.com/weibo?q=%E4%B8%96%E7%95%8C%E6%9D%AF";

        String result = service.shorten(originalUrl);

        assertThat(result).isEqualTo("https://s.example.com/abcde");
        HttpRequest request = sentRequest();
        assertThat(request.uri()).hasToString("https://s.example.com/api/link/create");
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer site-token");
        JsonNode body = objectMapper.readTree(readBody(request));
        assertThat(body.path("url").asText()).isEqualTo(originalUrl);
    }

    @Test
    void appliesShortLinksToAnyEnabledChannelProvider() throws Exception {
        when(configService.getSinkConfig())
                .thenReturn(new ApplicationConfigService.SinkConfig("https://s.example.com", "site-token"));
        stubResponse(200, "{\"link\":{\"slug\":\"abcde\"}}");
        Channel channel = channel("dingtalk", true);
        HotSearchItem item = new HotSearchItem(1, "世界杯", "热", 100L, false,
                "https://s.weibo.com/weibo?q=test");

        List<HotSearchItem> result = service.shortenItems(channel, List.of(item));

        assertThat(result).singleElement().extracting(HotSearchItem::url)
                .isEqualTo("https://s.example.com/abcde");
    }

    @Test
    void leavesUrlsUntouchedWhenChannelSwitchIsOff() throws Exception {
        when(configService.getSinkConfig())
                .thenReturn(new ApplicationConfigService.SinkConfig("https://s.example.com", "token"));
        Channel channel = channel("telegram", false);
        HotSearchItem item = new HotSearchItem(1, "世界杯", "热", 100L, false,
                "https://s.weibo.com/weibo?q=test");

        assertThat(service.shortenItems(channel, List.of(item))).containsExactly(item);
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void fallsBackToOriginalUrlsWhenSinkFails() throws Exception {
        when(configService.getSinkConfig())
                .thenReturn(new ApplicationConfigService.SinkConfig("https://s.example.com", "token"));
        stubResponse(500, "internal error");
        Channel channel = channel("wecom", true);
        HotSearchItem item = new HotSearchItem(1, "世界杯", "热", 100L, false,
                "https://s.weibo.com/weibo?q=test");

        List<HotSearchItem> result = service.shortenItems(channel, List.of(item));

        assertThat(result).containsExactly(item);
    }

    private Channel channel(String provider, boolean shortLinkEnabled) {
        Channel channel = new Channel();
        channel.setId(7L);
        channel.setProvider(provider);
        channel.setConfigMap(Map.of("shortLinkEnabled", shortLinkEnabled));
        return channel;
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body) throws Exception {
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(httpClient.<String>send(any(), any())).thenReturn(response);
    }

    private HttpRequest sentRequest() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        return captor.getValue();
    }

    /** 从 HttpRequest 的 BodyPublisher 中同步读出请求体文本。 */
    private static String readBody(HttpRequest request) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }
            @Override public void onNext(ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                out.writeBytes(bytes);
            }
            @Override public void onError(Throwable throwable) { done.countDown(); }
            @Override public void onComplete() { done.countDown(); }
        });
        try {
            if (!done.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("读取请求体超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
