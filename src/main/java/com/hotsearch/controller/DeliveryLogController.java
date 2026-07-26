package com.hotsearch.controller;

import com.hotsearch.config.CurrentUserId;
import com.hotsearch.dto.DeliveryLogEntry;
import com.hotsearch.service.DeliveryService;
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

    public DeliveryLogController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    @Operation(summary = "获取个人推送日志（按关键词聚合，含各通道推送详情）")
    public ResponseEntity<List<DeliveryLogEntry>> getRecentForUser(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "24") int hours) {
        int safeHours = Math.max(1, Math.min(hours, 168));
        return ResponseEntity.ok(deliveryService.getRecentByUser(userId, safeHours));
    }

    @DeleteMapping
    @Operation(summary = "清空当前用户推送日志（重置当前用户去重状态）")
    public ResponseEntity<Map<String, String>> clearForUser(@CurrentUserId Long userId) {
        deliveryService.clearByUser(userId);
        return ResponseEntity.ok(Map.of("message", "当前用户推送日志已清空，去重状态已重置"));
    }
}
