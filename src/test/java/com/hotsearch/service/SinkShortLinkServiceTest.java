package com.hotsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SinkShortLinkServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationConfigService configService = mock(ApplicationConfigService.class);
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/link/create", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"link\":{\"slug\":\"abcde\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void createsShortLinkUsingGlobalSinkConfiguration() throws Exception {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        when(configService.getSinkConfig())
                .thenReturn(new ApplicationConfigService.SinkConfig(baseUrl + "/", "site-token"));
        SinkShortLinkService service = new SinkShortLinkService(objectMapper, configService);
        String originalUrl = "https://s.weibo.com/weibo?q=%E4%B8%96%E7%95%8C%E6%9D%AF";

        String result = service.shorten(originalUrl);

        assertThat(result).isEqualTo(baseUrl + "/abcde");
        assertThat(authorization.get()).isEqualTo("Bearer site-token");
        JsonNode request = objectMapper.readTree(requestBody.get());
        assertThat(request.path("url").asText()).isEqualTo(originalUrl);
    }

    @Test
    void appliesShortLinksToAnyEnabledChannelProvider() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        when(configService.getSinkConfig())
                .thenReturn(new ApplicationConfigService.SinkConfig(baseUrl, "site-token"));
        SinkShortLinkService service = new SinkShortLinkService(objectMapper, configService);
        Channel channel = new Channel();
        channel.setId(7L);
        channel.setProvider("dingtalk");
        channel.setConfigMap(Map.of("shortLinkEnabled", true));
        HotSearchItem item = new HotSearchItem(1, "世界杯", "热", 100L, false,
                "https://s.weibo.com/weibo?q=test");

        List<HotSearchItem> result = service.shortenItems(channel, List.of(item));

        assertThat(result).singleElement().extracting(HotSearchItem::url)
                .isEqualTo(baseUrl + "/abcde");
    }

    @Test
    void leavesUrlsUntouchedWhenChannelSwitchIsOff() {
        when(configService.getSinkConfig())
                .thenReturn(new ApplicationConfigService.SinkConfig("https://s.example.com", "token"));
        SinkShortLinkService service = new SinkShortLinkService(objectMapper, configService);
        Channel channel = new Channel();
        channel.setProvider("telegram");
        channel.setConfigMap(Map.of("shortLinkEnabled", false));
        HotSearchItem item = new HotSearchItem(1, "世界杯", "热", 100L, false,
                "https://s.weibo.com/weibo?q=test");

        assertThat(service.shortenItems(channel, List.of(item))).containsExactly(item);
    }
}
