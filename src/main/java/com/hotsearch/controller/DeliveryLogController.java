package com.hotsearch.controller;

import com.hotsearch.dto.DeliveryLogResponse;
import com.hotsearch.service.DeliveryService;
import com.hotsearch.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    @Operation(summary = "获取个人推送日志")
    public ResponseEntity<List<DeliveryLogResponse>> getRecentForUser(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "24") int hours) {
        // Limit hours to reasonable range (1-168 hours = 1 week max)
        int safeHours = Math.max(1, Math.min(hours, 168));
        Long userId = getUserId(authHeader);
        return ResponseEntity.ok(deliveryService.getRecentLogsByUser(userId, safeHours));
    }
}
