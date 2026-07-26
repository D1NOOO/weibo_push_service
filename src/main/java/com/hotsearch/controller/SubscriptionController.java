package com.hotsearch.controller;

import com.hotsearch.config.CurrentUserId;
import com.hotsearch.dto.SubscriptionRequest;
import com.hotsearch.dto.SubscriptionResponse;
import com.hotsearch.dto.EnabledRequest;
import com.hotsearch.service.SubscriptionService;
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

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    @Operation(summary = "获取订阅列表")
    public ResponseEntity<List<SubscriptionResponse>> list(@CurrentUserId Long userId) {
        return ResponseEntity.ok(subscriptionService.listByUser(userId));
    }

    @GetMapping("/history")
    @Operation(summary = "获取已过期订阅列表")
    public ResponseEntity<List<SubscriptionResponse>> history(@CurrentUserId Long userId) {
        return ResponseEntity.ok(subscriptionService.listExpiredByUser(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取单个订阅")
    public ResponseEntity<SubscriptionResponse> getById(
            @CurrentUserId Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getById(userId, id));
    }

    @PostMapping
    @Operation(summary = "创建订阅")
    public ResponseEntity<SubscriptionResponse> create(
            @CurrentUserId Long userId,
            @Valid @RequestBody SubscriptionRequest req) {
        return ResponseEntity.ok(subscriptionService.create(userId, req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新订阅")
    public ResponseEntity<SubscriptionResponse> update(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionRequest req) {
        return ResponseEntity.ok(subscriptionService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除订阅")
    public ResponseEntity<Void> delete(
            @CurrentUserId Long userId,
            @PathVariable Long id) {
        subscriptionService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/enabled")
    @Operation(summary = "启用或禁用订阅")
    public ResponseEntity<SubscriptionResponse> updateEnabled(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @Valid @RequestBody EnabledRequest req) {
        return ResponseEntity.ok(subscriptionService.updateEnabled(userId, id, req.enabled()));
    }
}
