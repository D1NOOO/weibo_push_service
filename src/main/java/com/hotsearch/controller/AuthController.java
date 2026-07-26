package com.hotsearch.controller;

import com.hotsearch.config.RateLimiter;
import com.hotsearch.dto.ChangePasswordRequest;
import com.hotsearch.dto.LoginRequest;
import com.hotsearch.dto.TokenResponse;
import com.hotsearch.dto.WxLoginRequest;
import com.hotsearch.dto.WxLoginResponse;
import com.hotsearch.entity.User;
import com.hotsearch.repository.UserRepository;
import com.hotsearch.service.AuthService;
import com.hotsearch.service.WxAuthService;
import com.hotsearch.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "用户登录与密码管理")
public class AuthController {

    private final AuthService authService;
    private final WxAuthService wxAuthService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RateLimiter rateLimiter;

    public AuthController(AuthService authService, WxAuthService wxAuthService,
                          UserRepository userRepository, JwtUtil jwtUtil, RateLimiter rateLimiter) {
        this.authService = authService;
        this.wxAuthService = wxAuthService;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "返回JWT令牌。首次登录需修改密码。")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req,
                                                HttpServletRequest request) {
        String clientIp = getClientIp(request);
        if (!rateLimiter.tryAcquire(clientIp)) {
            long waitSec = rateLimiter.remainingSeconds(clientIp);
            throw new RuntimeException("登录尝试过于频繁，请在 " + waitSec + " 秒后重试");
        }
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/wx-login")
    @Operation(summary = "微信小程序登录", description = "提交 wx.login 获取的 code，返回主服务 JWT。首次登录自动创建用户。")
    public ResponseEntity<WxLoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest req,
                                                   HttpServletRequest request) {
        String clientIp = getClientIp(request);
        if (!rateLimiter.tryAcquire("wx:" + clientIp)) {
            long waitSec = rateLimiter.remainingSeconds("wx:" + clientIp);
            throw new RuntimeException("登录尝试过于频繁，请在 " + waitSec + " 秒后重试");
        }
        return ResponseEntity.ok(wxAuthService.wxLogin(req));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("username", user.getUsername());
        body.put("nickname", user.getNickname());
        body.put("avatar", user.getAvatar());
        body.put("role", user.getRole());
        body.put("wxBound", user.getOpenid() != null && !user.getOpenid().isBlank());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改密码")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ChangePasswordRequest req) {
        String token = authHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserId(token);
        authService.changePassword(userId, req);
        return ResponseEntity.ok(Map.of("message", "密码修改成功"));
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
