package com.hotsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.config.WxProperties;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * 微信 access_token 缓存（仅 openapi 调用模式需要）。
 * 注意：获取 access_token 要求服务器出口 IP 在小程序后台白名单内。
 */
@Service
public class WxAccessTokenService {

    private static final Logger log = LoggerFactory.getLogger(WxAccessTokenService.class);
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";

    private final WxProperties wxProperties;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public WxAccessTokenService(WxProperties wxProperties, ObjectMapper objectMapper) {
        this.wxProperties = wxProperties;
        this.objectMapper = objectMapper;
    }

    public synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
            return cachedToken;
        }
        if (!wxProperties.isLoginConfigured()) {
            throw new RuntimeException("微信配置缺失：请设置 WX_APPID 和 WX_SECRET");
        }
        try {
            String url = TOKEN_URL + "?grant_type=client_credential"
                    + "&appid=" + URLEncoder.encode(wxProperties.getAppid(), StandardCharsets.UTF_8)
                    + "&secret=" + URLEncoder.encode(wxProperties.getSecret(), StandardCharsets.UTF_8);
            String body = Jsoup.connect(url).ignoreContentType(true).timeout(10_000).execute().body();
            Map<String, Object> resp = objectMapper.readValue(body, Map.class);
            Object errcode = resp.get("errcode");
            if (errcode instanceof Number number && number.intValue() != 0) {
                throw new RuntimeException("获取微信 access_token 失败 errcode=" + number + ": " + resp.get("errmsg"));
            }
            Object token = resp.get("access_token");
            if (token == null || String.valueOf(token).isBlank()) {
                throw new RuntimeException("获取微信 access_token 失败：返回为空");
            }
            long expiresIn = resp.get("expires_in") instanceof Number n ? n.longValue() : 7200L;
            cachedToken = String.valueOf(token);
            // 提前 5 分钟过期，避免边界失效
            expiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 300));
            return cachedToken;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取微信 access_token 失败", e);
            throw new RuntimeException("获取微信 access_token 失败：" + e.getMessage());
        }
    }

    public synchronized void invalidate() {
        cachedToken = null;
        expiresAt = Instant.EPOCH;
    }
}
