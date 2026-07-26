package com.hotsearch.controller;

import com.hotsearch.service.NotificationQuotaService;
import com.hotsearch.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wx/subscribe-message")
@Tag(name = "微信订阅消息", description = "订阅消息额度查询与授权上报")
public class WxSubscribeMessageController {

    public record GrantRequest(@NotNull(message = "acceptedTemplateIds不能为空") List<String> acceptedTemplateIds) {}

    private final NotificationQuotaService quotaService;
    private final JwtUtil jwtUtil;

    public WxSubscribeMessageController(NotificationQuotaService quotaService, JwtUtil jwtUtil) {
        this.quotaService = quotaService;
        this.jwtUtil = jwtUtil;
    }

    private Long getUserId(String authHeader) {
        return jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    }

    @GetMapping("/quota")
    @Operation(summary = "查询可发送额度", description = "返回全局模板 ID 与当前用户各模板剩余额度")
    public ResponseEntity<Map<String, Object>> quota(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(quotaService.quotaSummary(getUserId(authHeader)));
    }

    @PostMapping("/grant")
    @Operation(summary = "上报订阅消息授权结果", description = "小程序 wx.requestSubscribeMessage 后回传 accepted 的模板 ID，每个 +1 次额度")
    public ResponseEntity<Map<String, Object>> grant(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody GrantRequest req) {
        return ResponseEntity.ok(quotaService.grant(getUserId(authHeader), req.acceptedTemplateIds()));
    }
}
