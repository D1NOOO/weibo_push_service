package com.hotsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SinkShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(SinkShortLinkService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final ApplicationConfigService configService;
    private final HttpClient httpClient;

    @Autowired
    public SinkShortLinkService(ObjectMapper objectMapper, ApplicationConfigService configService) {
        this(objectMapper, configService, HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build());
    }

    SinkShortLinkService(ObjectMapper objectMapper, ApplicationConfigService configService, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.httpClient = httpClient;
    }

    public boolean isEnabled(Channel channel) {
        return channel != null
                && Boolean.TRUE.equals(channel.getConfigMap().get("shortLinkEnabled"))
                && configService.getSinkConfig().isConfigured();
    }

    public List<HotSearchItem> shortenItems(Channel channel, List<HotSearchItem> items) {
        if (!isEnabled(channel) || items == null || items.isEmpty()) return items;

        ApplicationConfigService.SinkConfig sinkConfig = configService.getSinkConfig();
        Map<String, String> shortenedUrls = new HashMap<>();
        List<HotSearchItem> shortenedItems = new ArrayList<>(items.size());
        try {
            for (HotSearchItem item : items) {
                String url = item.url();
                String shortenedUrl = url;
                if (url != null && !url.isBlank()) {
                    shortenedUrl = shortenedUrls.computeIfAbsent(url,
                            originalUrl -> shorten(sinkConfig, originalUrl));
                }
                shortenedItems.add(new HotSearchItem(item.rank(), item.keyword(), item.label(), item.hotValue(),
                        item.isAd(), shortenedUrl));
            }
            return List.copyOf(shortenedItems);
        } catch (RuntimeException e) {
            log.warn("Sink short-link generation failed for channel {}; original URLs will be used: {}",
                    channel.getId(), e.getMessage());
            return items;
        }
    }

    public String shorten(String originalUrl) {
        return shorten(configService.getSinkConfig(), originalUrl);
    }

    private String shorten(ApplicationConfigService.SinkConfig sinkConfig, String originalUrl) {
        if (!sinkConfig.isConfigured()) return originalUrl;

        String baseUrl = normalizeBaseUrl(sinkConfig.baseUrl());
        try {
            String json = objectMapper.writeValueAsString(Map.of("url", originalUrl));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/link/create"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + sinkConfig.token())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Sink API returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String slug = root.path("link").path("slug").asText("").trim();
            if (slug.isEmpty()) {
                throw new IllegalStateException("Sink API response is missing link.slug");
            }
            return URI.create(baseUrl + "/" + slug).toASCIIString();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Sink API request failed: " + e.getMessage(), e);
        }
    }

    private String normalizeBaseUrl(String value) {
        String baseUrl = value.trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Sink Base URL is invalid", e);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalStateException("Sink Base URL must use HTTP or HTTPS");
        }
        return baseUrl;
    }
}
