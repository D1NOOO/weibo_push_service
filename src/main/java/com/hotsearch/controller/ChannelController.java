package com.hotsearch.controller;

import com.hotsearch.config.CurrentUserId;
import com.hotsearch.dto.ChannelRequest;
import com.hotsearch.dto.ChannelResponse;
import com.hotsearch.dto.EnabledRequest;
import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import com.hotsearch.exception.BusinessException;
import com.hotsearch.provider.MessageProvider;
import com.hotsearch.provider.PushMessage;
import com.hotsearch.service.ChannelService;
import com.hotsearch.service.SinkShortLinkService;
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
    private final Map<String, MessageProvider> providerMap;
    private final SinkShortLinkService sinkShortLinkService;

    public ChannelController(ChannelService channelService,
                             Map<String, MessageProvider> providerMap,
                             SinkShortLinkService sinkShortLinkService) {
        this.channelService = channelService;
        this.providerMap = providerMap;
        this.sinkShortLinkService = sinkShortLinkService;
    }

    @GetMapping
    @Operation(summary = "获取通道列表")
    public ResponseEntity<List<ChannelResponse>> list(@CurrentUserId Long userId) {
        return ResponseEntity.ok(channelService.listByUser(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取单个通道")
    public ResponseEntity<ChannelResponse> getById(
            @CurrentUserId Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(channelService.getById(userId, id));
    }

    @PostMapping
    @Operation(summary = "创建通道")
    public ResponseEntity<ChannelResponse> create(
            @CurrentUserId Long userId,
            @Valid @RequestBody ChannelRequest req) {
        return ResponseEntity.ok(channelService.create(userId, req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新通道")
    public ResponseEntity<ChannelResponse> update(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ChannelRequest req) {
        return ResponseEntity.ok(channelService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除通道")
    public ResponseEntity<Void> delete(
            @CurrentUserId Long userId,
            @PathVariable Long id) {
        channelService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/enabled")
    @Operation(summary = "启用或禁用通道")
    public ResponseEntity<ChannelResponse> updateEnabled(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @Valid @RequestBody EnabledRequest req) {
        return ResponseEntity.ok(channelService.updateEnabled(userId, id, req.enabled()));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "发送测试消息")
    public ResponseEntity<Map<String, String>> test(
            @CurrentUserId Long userId,
            @PathVariable Long id) {
        Channel ch = channelService.getEntityById(userId, id);

        MessageProvider provider = providerMap.get(ch.getProvider());
        if (provider == null) {
            throw new BusinessException("未知的推送提供者: " + ch.getProvider());
        }

        HotSearchItem testItem = new HotSearchItem(
                1, "测试热搜", "热", 99999L, false,
                "https://s.weibo.com/weibo?q=测试热搜");
        List<HotSearchItem> items = sinkShortLinkService.shortenItems(ch, List.of(testItem));

        List<String> targets = provider.getTargets(ch);
        if (targets.isEmpty()) {
            throw new BusinessException("通道未配置目标聊天");
        }
        for (String target : targets) {
            provider.send(new PushMessage(ch, items, target, null));
        }
        return ResponseEntity.ok(Map.of("message", "测试消息发送成功"));
    }
}
