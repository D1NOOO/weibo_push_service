package com.hotsearch.controller;

import com.hotsearch.dto.ChannelRequest;
import com.hotsearch.dto.ChannelResponse;
import com.hotsearch.service.ChannelService;
import com.hotsearch.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
@Tag(name = "通道管理", description = "推送通道CRUD")
public class ChannelController {

    private final ChannelService channelService;
    private final JwtUtil jwtUtil;

    public ChannelController(ChannelService channelService, JwtUtil jwtUtil) {
        this.channelService = channelService;
        this.jwtUtil = jwtUtil;
    }

    private Long getUserId(String authHeader) {
        return jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    }

    @GetMapping
    @Operation(summary = "获取通道列表")
    public ResponseEntity<List<ChannelResponse>> list(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(channelService.listByUser(getUserId(authHeader)));
    }

    @PostMapping
    @Operation(summary = "创建通道")
    public ResponseEntity<ChannelResponse> create(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ChannelRequest req) {
        return ResponseEntity.ok(channelService.create(getUserId(authHeader), req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新通道")
    public ResponseEntity<ChannelResponse> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @Valid @RequestBody ChannelRequest req) {
        return ResponseEntity.ok(channelService.update(getUserId(authHeader), id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除通道")
    public ResponseEntity<Void> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        channelService.delete(getUserId(authHeader), id);
        return ResponseEntity.noContent().build();
    }
}
