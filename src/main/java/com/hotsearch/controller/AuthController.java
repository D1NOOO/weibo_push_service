package com.hotsearch.controller;

import com.hotsearch.config.CurrentUserId;
import com.hotsearch.config.RateLimiter;
import com.hotsearch.dto.ChangePasswordRequest;
import com.hotsearch.dto.LoginRequest;
import com.hotsearch.dto.TokenResponse;
import com.hotsearch.exception.ApiException;
import com.hotsearch.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "用户登录与密码管理")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    public AuthController(AuthService authService, RateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "返回JWT令牌。首次登录需修改密码。")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req,
                                                HttpServletRequest request) {
        String clientIp = getClientIp(request);
        if (!rateLimiter.tryAcquire(clientIp)) {
            long waitSec = rateLimiter.remainingSeconds(clientIp);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "登录尝试过于频繁，请在 " + waitSec + " 秒后重试");
        }
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改密码")
    public ResponseEntity<Map<String, String>> changePassword(
            @CurrentUserId Long userId,
            @Valid @RequestBody ChangePasswordRequest req) {
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
