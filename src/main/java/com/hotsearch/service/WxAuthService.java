package com.hotsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.config.WxProperties;
import com.hotsearch.dto.WxLoginRequest;
import com.hotsearch.dto.WxLoginResponse;
import com.hotsearch.entity.User;
import com.hotsearch.repository.UserRepository;
import com.hotsearch.util.JwtUtil;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class WxAuthService {

    private static final Logger log = LoggerFactory.getLogger(WxAuthService.class);
    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final char[] USERNAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WxProperties wxProperties;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public WxAuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                         JwtUtil jwtUtil, WxProperties wxProperties, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.wxProperties = wxProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WxLoginResponse wxLogin(WxLoginRequest req) {
        if (!wxProperties.isLoginConfigured()) {
            throw new RuntimeException("微信登录未配置：请设置 WX_APPID 和 WX_SECRET");
        }

        SessionResult session = code2Session(req.code());

        boolean[] created = {false};
        User user = userRepository.findByOpenid(session.openid()).orElseGet(() -> {
            created[0] = true;
            return createWxUser(session.openid());
        });

        if (session.unionid() != null && !session.unionid().isBlank()) {
            user.setUnionid(session.unionid());
        }
        if (req.nickname() != null && !req.nickname().isBlank()) {
            user.setNickname(req.nickname().trim());
        }
        if (req.avatar() != null && !req.avatar().isBlank()) {
            user.setAvatar(req.avatar().trim());
        }
        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new WxLoginResponse(token, user.getId(), user.getUsername(),
                user.getNickname(), user.getAvatar(), created[0]);
    }

    private User createWxUser(String openid) {
        User user = new User();
        user.setUsername(generateUsername());
        // 微信用户不走密码登录，写入随机密码哈希占位
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole("USER");
        user.setMustChangePassword(false);
        user.setOpenid(openid);
        return user;
    }

    private String generateUsername() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder("wx_");
            for (int i = 0; i < 10; i++) {
                sb.append(USERNAME_CHARS[random.nextInt(USERNAME_CHARS.length)]);
            }
            String candidate = sb.toString();
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new RuntimeException("生成用户名失败，请重试");
    }

    private SessionResult code2Session(String code) {
        try {
            String url = CODE2SESSION_URL
                    + "?appid=" + URLEncoder.encode(wxProperties.getAppid(), StandardCharsets.UTF_8)
                    + "&secret=" + URLEncoder.encode(wxProperties.getSecret(), StandardCharsets.UTF_8)
                    + "&js_code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";
            String body = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .timeout(10_000)
                    .execute()
                    .body();

            Map<String, Object> resp = objectMapper.readValue(body, Map.class);
            Object errcode = resp.get("errcode");
            if (errcode instanceof Number number && number.intValue() != 0) {
                throw new RuntimeException(code2SessionError(number.intValue(), resp.get("errmsg")));
            }
            Object openid = resp.get("openid");
            if (openid == null || String.valueOf(openid).isBlank()) {
                throw new RuntimeException("微信登录失败：未返回 openid");
            }
            Object unionid = resp.get("unionid");
            return new SessionResult(String.valueOf(openid),
                    unionid == null ? null : String.valueOf(unionid));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("code2session 调用失败", e);
            throw new RuntimeException("微信登录失败：无法连接微信服务");
        }
    }

    private String code2SessionError(int errcode, Object errmsg) {
        return switch (errcode) {
            case 40029 -> "微信登录失败：code 无效，请重新登录";
            case 45011 -> "微信登录过于频繁，请稍后重试";
            case 40226 -> "微信登录失败：账号存在风险，已被拦截";
            case 40013 -> "微信登录失败：AppID 配置错误";
            default -> "微信登录失败（errcode=" + errcode + "）：" + (errmsg == null ? "" : errmsg);
        };
    }

    private record SessionResult(String openid, String unionid) {}
}
