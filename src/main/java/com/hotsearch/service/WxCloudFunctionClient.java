package com.hotsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.config.WxProperties;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 主服务 -> 微信云函数的签名调用客户端。
 *
 * 载荷格式：{ timestamp, nonce, signature, message }，其中 message 为原始 JSON 字符串，
 * signature = HMAC-SHA256(sharedSecret, "timestamp.nonce.message")。
 * 基于原始字符串签名可避免 Java/Node 两侧 JSON 重序列化差异。
 *
 * 两种调用模式：
 * - http-trigger：POST 云开发「HTTP 访问服务」绑定的公网 URL，无需 access_token
 * - openapi：POST api.weixin.qq.com/tcb/invokecloudfunction，需 access_token（IP 白名单）
 */
@Service
public class WxCloudFunctionClient {

    public record InvokeResult(boolean ok, Integer errcode, String errmsg) {
        public boolean isQuotaExhausted() {
            return errcode != null && errcode == 43101;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(WxCloudFunctionClient.class);
    private static final String INVOKE_URL = "https://api.weixin.qq.com/tcb/invokecloudfunction";

    private final WxProperties wxProperties;
    private final WxAccessTokenService accessTokenService;
    private final ObjectMapper objectMapper;

    public WxCloudFunctionClient(WxProperties wxProperties, WxAccessTokenService accessTokenService,
                                 ObjectMapper objectMapper) {
        this.wxProperties = wxProperties;
        this.accessTokenService = accessTokenService;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return wxProperties.getCloud().isConfigured();
    }

    /** 调用云函数发送订阅消息。message 至少包含 openid/templateId/data。 */
    public InvokeResult sendSubscribeMessage(Map<String, Object> message) {
        WxProperties.Cloud cloud = wxProperties.getCloud();
        if (!cloud.isConfigured()) {
            throw new RuntimeException("云函数调用未配置：请设置 WX_CLOUD_SHARED_SECRET 及调用地址/环境");
        }
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            long timestamp = System.currentTimeMillis();
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String signature = hmacSha256Hex(cloud.getSharedSecret(), timestamp + "." + nonce + "." + messageJson);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("timestamp", timestamp);
            payload.put("nonce", nonce);
            payload.put("signature", signature);
            payload.put("message", messageJson);
            String payloadJson = objectMapper.writeValueAsString(payload);

            String responseBody = "openapi".equalsIgnoreCase(cloud.getInvokeMode())
                    ? invokeViaOpenApi(cloud, payloadJson)
                    : invokeViaHttpTrigger(cloud, payloadJson);
            return parseFunctionResult(responseBody);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用云函数失败", e);
            throw new RuntimeException("调用云函数失败：" + e.getMessage());
        }
    }

    private String invokeViaHttpTrigger(WxProperties.Cloud cloud, String payloadJson) throws Exception {
        Connection.Response response = Jsoup.connect(cloud.getHttpTriggerUrl())
                .requestBody(payloadJson)
                .header("Content-Type", "application/json")
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .timeout(15_000)
                .method(Connection.Method.POST)
                .execute();
        if (response.statusCode() >= 400) {
            throw new RuntimeException("云函数 HTTP 访问服务返回 " + response.statusCode() + ": "
                    + truncate(response.body(), 200));
        }
        return response.body();
    }

    private String invokeViaOpenApi(WxProperties.Cloud cloud, String payloadJson) throws Exception {
        String body = doInvokeViaOpenApi(cloud, payloadJson, accessTokenService.getAccessToken());
        Map<String, Object> resp = objectMapper.readValue(body, Map.class);
        int errcode = resp.get("errcode") instanceof Number n ? n.intValue() : 0;
        if (errcode == 40001 || errcode == 42001) {
            // access_token 失效，刷新后重试一次
            accessTokenService.invalidate();
            body = doInvokeViaOpenApi(cloud, payloadJson, accessTokenService.getAccessToken());
            resp = objectMapper.readValue(body, Map.class);
            errcode = resp.get("errcode") instanceof Number n ? n.intValue() : 0;
        }
        if (errcode != 0) {
            throw new RuntimeException("invokecloudfunction 失败 errcode=" + errcode + ": " + resp.get("errmsg"));
        }
        Object respData = resp.get("resp_data");
        return respData == null ? "" : String.valueOf(respData);
    }

    private String doInvokeViaOpenApi(WxProperties.Cloud cloud, String payloadJson, String accessToken) throws Exception {
        String url = INVOKE_URL + "?access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                + "&env=" + URLEncoder.encode(cloud.getEnvId(), StandardCharsets.UTF_8)
                + "&name=" + URLEncoder.encode(cloud.getFunctionName(), StandardCharsets.UTF_8);
        return Jsoup.connect(url)
                .requestBody(payloadJson)
                .header("Content-Type", "application/json")
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .timeout(15_000)
                .method(Connection.Method.POST)
                .execute()
                .body();
    }

    private InvokeResult parseFunctionResult(String body) {
        if (body == null || body.isBlank()) {
            return new InvokeResult(false, null, "云函数返回为空");
        }
        try {
            Map<String, Object> result = objectMapper.readValue(body, Map.class);
            boolean ok = Boolean.TRUE.equals(result.get("ok"));
            Integer errcode = result.get("errcode") instanceof Number n ? n.intValue() : null;
            Object errmsg = result.get("errmsg") != null ? result.get("errmsg") : result.get("error");
            return new InvokeResult(ok, errcode, errmsg == null ? null : String.valueOf(errmsg));
        } catch (Exception e) {
            return new InvokeResult(false, null, "云函数返回无法解析: " + truncate(body, 200));
        }
    }

    private String hmacSha256Hex(String secret, String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
