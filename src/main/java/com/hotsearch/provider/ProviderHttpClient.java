package com.hotsearch.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Provider 统一的 HTTP 出口：显式超时、JSON 编解码、非 2xx 与网络错误统一转 ProviderException。
 * 错误信息只保留状态码与响应体片段，避免把含密钥的请求 URL 泄露到日志与接口响应。
 */
@Component
public class ProviderHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    private static final int BODY_SNIPPET_LIMIT = 200;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public ProviderHttpClient(ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** POST JSON 并将响应体解析为 JsonNode。 */
    public JsonNode postJson(String url, Object body) {
        return postJson(url, body, Map.of());
    }

    public JsonNode postJson(String url, Object body, Map<String, String> headers) {
        try {
            String requestJson = objectMapper.writeValueAsString(body);
            String responseBody = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        } catch (RestClientResponseException e) {
            throw new ProviderException(
                    "HTTP " + e.getStatusCode().value() + ": " + snippet(e.getResponseBodyAsString()), e);
        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new ProviderException("网络请求失败: " + cause.getMessage(), e);
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("请求失败: " + e.getMessage(), e);
        }
    }

    private String snippet(String body) {
        if (body == null || body.isBlank()) return "(无响应体)";
        String trimmed = body.trim();
        return trimmed.length() <= BODY_SNIPPET_LIMIT
                ? trimmed
                : trimmed.substring(0, BODY_SNIPPET_LIMIT) + "…";
    }
}
