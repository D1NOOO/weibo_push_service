package com.hotsearch.controller;

import com.hotsearch.service.PipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@Tag(name = "应用配置", description = "获取或修改应用配置")
public class ConfigController {

    private final PipelineService pipelineService;

    public ConfigController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @GetMapping
    @Operation(summary = "获取当前应用配置")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("dedupeWindowHours", pipelineService.getDedupeWindowHours());
        return ResponseEntity.ok(config);
    }

    @PutMapping
    @Operation(summary = "更新应用配置")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        if (body.containsKey("dedupeWindowHours")) {
            int hours = ((Number) body.get("dedupeWindowHours")).intValue();
            if (hours < 1 || hours > 168) {
                throw new RuntimeException("去重窗口时间必须在 1-168 小时之间");
            }
            pipelineService.setDedupeWindowHours(hours);
        }
        Map<String, Object> config = new HashMap<>();
        config.put("dedupeWindowHours", pipelineService.getDedupeWindowHours());
        return ResponseEntity.ok(config);
    }
}
