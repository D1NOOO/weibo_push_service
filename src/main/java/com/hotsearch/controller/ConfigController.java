package com.hotsearch.controller;

import com.hotsearch.service.ApplicationConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@Tag(name = "应用配置", description = "获取或修改应用配置")
public class ConfigController {

    private static final String MASKED_SECRET = "********";
    private final ApplicationConfigService configService;

    public ConfigController(ApplicationConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    @Operation(summary = "获取当前应用配置")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(responseConfig());
    }

    @PutMapping
    @Operation(summary = "更新应用配置")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        if (body.containsKey("dedupeWindowHours")) {
            int hours = numberValue(body.get("dedupeWindowHours"), "去重窗口");
            if (hours < 1 || hours > 168) {
                throw new RuntimeException("去重窗口必须在 1-168 小时之间");
            }
            configService.setDedupeWindowHours(hours);
        }

        if (body.containsKey("fetchIntervalMinutes")) {
            int minutes = numberValue(body.get("fetchIntervalMinutes"), "抓取频率");
            if (minutes < 1 || minutes > 1_440) {
                throw new RuntimeException("抓取频率必须在 1-1440 分钟之间");
            }
            configService.setFetchIntervalMinutes(minutes);
        }

        if (body.containsKey("sinkBaseUrl") || body.containsKey("sinkToken")) {
            ApplicationConfigService.SinkConfig current = configService.getSinkConfig();
            String baseUrl = body.containsKey("sinkBaseUrl")
                    ? stringValue(body.get("sinkBaseUrl")) : current.baseUrl();
            String requestedToken = body.containsKey("sinkToken")
                    ? stringValue(body.get("sinkToken")) : MASKED_SECRET;
            String token = isMaskedSecret(requestedToken) ? current.token() : requestedToken;
            validateSinkConfig(baseUrl, token);
            configService.setSinkConfig(baseUrl, token);
        }

        return ResponseEntity.ok(responseConfig());
    }

    private Map<String, Object> responseConfig() {
        ApplicationConfigService.SinkConfig sink = configService.getSinkConfig();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("dedupeWindowHours", configService.getDedupeWindowHours());
        config.put("fetchIntervalMinutes", configService.getFetchIntervalMinutes());
        config.put("sinkBaseUrl", sink.baseUrl());
        config.put("sinkToken", sink.token().isBlank() ? "" : MASKED_SECRET);
        config.put("sinkConfigured", sink.isConfigured());
        return config;
    }

    private void validateSinkConfig(String baseUrl, String token) {
        if (baseUrl.isBlank() && token.isBlank()) return;
        if (baseUrl.isBlank() || token.isBlank()) {
            throw new RuntimeException("Sink Base URL 和 Site Token 必须同时配置");
        }
        try {
            URI uri = URI.create(baseUrl);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Sink Base URL 必须是有效的 HTTP/HTTPS 地址");
        }
    }

    private int numberValue(Object value, String fieldName) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException e) {
            throw new RuntimeException(fieldName + "必须是整数");
        }
    }

    private boolean isMaskedSecret(String value) {
        return !value.isBlank() && value.chars().allMatch(ch -> ch == '*');
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
