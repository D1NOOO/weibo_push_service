package com.hotsearch.controller;

import com.hotsearch.dto.SubscriptionRequest;
import com.hotsearch.dto.SubscriptionResponse;
import com.hotsearch.service.SubscriptionService;
import com.hotsearch.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@Tag(name = "订阅管理", description = "热搜订阅CRUD")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final JwtUtil jwtUtil;

    public SubscriptionController(SubscriptionService subscriptionService, JwtUtil jwtUtil) {
        this.subscriptionService = subscriptionService;
        this.jwtUtil = jwtUtil;
    }

    private Long getUserId(String authHeader) {
        return jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    }

    @GetMapping
    @Operation(summary = "获取订阅列表")
    public ResponseEntity<List<SubscriptionResponse>> list(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(subscriptionService.listByUser(getUserId(authHeader)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取单个订阅")
    public ResponseEntity<SubscriptionResponse> getById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getById(getUserId(authHeader), id));
    }

    @PostMapping
    @Operation(summary = "创建订阅")
    public ResponseEntity<SubscriptionResponse> create(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody SubscriptionRequest req) {
        return ResponseEntity.ok(subscriptionService.create(getUserId(authHeader), req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新订阅")
    public ResponseEntity<SubscriptionResponse> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionRequest req) {
        return ResponseEntity.ok(subscriptionService.update(getUserId(authHeader), id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除订阅")
    public ResponseEntity<Void> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        subscriptionService.delete(getUserId(authHeader), id);
        return ResponseEntity.noContent().build();
    }
}
