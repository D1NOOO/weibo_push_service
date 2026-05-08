package com.hotsearch.controller;

import com.hotsearch.dto.ChannelRequest;
import com.hotsearch.dto.ChannelResponse;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import com.hotsearch.provider.MessageProvider;
import com.hotsearch.service.ChannelService;
import com.hotsearch.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/channels")
@Tag(name = "通道管理", description = "推送通道CRUD")
public class ChannelController {

    private final ChannelService channelService;
    private final JwtUtil jwtUtil;
    private final Map<String, MessageProvider> providerMap;

    public ChannelController(ChannelService channelService, JwtUtil jwtUtil,
                             Map<String, MessageProvider> providerMap) {
        this.channelService = channelService;
        this.jwtUtil = jwtUtil;
        this.providerMap = providerMap;
    }

    private Long getUserId(String authHeader) {
        return jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
    }

    @GetMapping
    @Operation(summary = "获取通道列表")
    public ResponseEntity<List<ChannelResponse>> list(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(channelService.listByUser(getUserId(authHeader)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取单个通道")
    public ResponseEntity<ChannelResponse> getById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        return ResponseEntity.ok(channelService.getById(getUserId(authHeader), id));
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

    @PostMapping("/{id}/test")
    @Operation(summary = "发送测试消息")
    public ResponseEntity<Map<String, String>> test(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long userId = getUserId(authHeader);
        ChannelResponse ch = channelService.getById(userId, id);

        MessageProvider provider = providerMap.get(ch.provider());
        if (provider == null) {
            throw new RuntimeException("未知的推送提供者: " + ch.provider());
        }

        HotSearchItem testItem = new HotSearchItem(
                1, "测试热搜", "热", 99999L, false,
                "https://s.weibo.com/weibo?q=测试热搜");
        List<HotSearchItem> allItems = List.of(testItem);

        Channel channel = new Channel();
        channel.setProvider(ch.provider());
        channel.setConfigMap(ch.config());

        provider.send(channel, testItem, allItems);
        return ResponseEntity.ok(Map.of("message", "测试消息发送成功"));
    }
}
