package com.hotsearch.controller;

import com.hotsearch.dto.DeliveryLogEntry;
import com.hotsearch.service.DeliveryService;
import com.hotsearch.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/delivery-logs")
@Tag(name = "推送日志", description = "查看推送记录")
public class DeliveryLogController {

    private final DeliveryService deliveryService;
    private final JwtUtil jwtUtil;

    public DeliveryLogController(DeliveryService deliveryService, JwtUtil jwtUtil) {
        this.deliveryService = deliveryService;
        this.jwtUtil = jwtUtil;
    }

    private Long getUserId(String authHeader) {
        return jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    }

    @GetMapping
    @Operation(summary = "获取个人推送日志（按关键词聚合，含各通道推送详情）")
    public ResponseEntity<List<DeliveryLogEntry>> getRecentForUser(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "24") int hours) {
        int safeHours = Math.max(1, Math.min(hours, 168));
        Long userId = getUserId(authHeader);
        return ResponseEntity.ok(deliveryService.getRecentByUser(userId, safeHours));
    }

    @DeleteMapping
    @Operation(summary = "清空所有推送日志（重置去重状态）")
    public ResponseEntity<Map<String, String>> clearAll() {
        deliveryService.clearAll();
        return ResponseEntity.ok(Map.of("message", "推送日志已清空，去重状态已重置"));
    }
}
