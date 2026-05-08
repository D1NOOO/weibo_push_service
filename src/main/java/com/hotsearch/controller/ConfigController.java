package com.hotsearch.controller;

import com.hotsearch.service.PipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@Tag(name = "应用配置", description = "获取或修改应用配置")
public class ConfigController {

    private final PipelineService pipelineService;

    @Value("${app.dedupe.window-hours:6}")
    private int dedupeWindowHours;

    public ConfigController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @GetMapping
    @Operation(summary = "获取当前应用配置")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("dedupeWindowHours", dedupeWindowHours);
        config.put("description", "当前去重窗口时间，表示在多长时间内对同一个话题不再重复推送");
        return ResponseEntity.ok(config);
    }
}