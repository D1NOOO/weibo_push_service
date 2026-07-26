package com.hotsearch.controller;

import com.hotsearch.dto.MatchEventResponse;
import com.hotsearch.service.MatchEventService;
import com.hotsearch.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/match-events")
@Tag(name = "命中事件", description = "订阅命中事件查询")
public class MatchEventController {

    private final MatchEventService matchEventService;
    private final JwtUtil jwtUtil;

    public MatchEventController(MatchEventService matchEventService, JwtUtil jwtUtil) {
        this.matchEventService = matchEventService;
        this.jwtUtil = jwtUtil;
    }

    private Long getUserId(String authHeader) {
        return jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    }

    @GetMapping
    @Operation(summary = "获取命中事件列表", description = "按事件聚合的命中记录，含今日新增/活跃统计")
    public ResponseEntity<MatchEventResponse.Summary> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "72") int hours,
            @RequestParam(required = false) Long subscriptionId) {
        int safeHours = Math.max(1, Math.min(hours, 720));
        return ResponseEntity.ok(matchEventService.listByUser(getUserId(authHeader), safeHours, subscriptionId));
    }
}
