package com.hotsearch.controller;

import com.hotsearch.dto.HotSearchResult;
import com.hotsearch.service.HotSearchService;
import com.hotsearch.service.PipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hotsearch")
@Tag(name = "热搜数据", description = "热搜查询与手动触发")
public class HotSearchController {

    private final HotSearchService hotSearchService;
    private final PipelineService pipelineService;

    public HotSearchController(HotSearchService hotSearchService, PipelineService pipelineService) {
        this.hotSearchService = hotSearchService;
        this.pipelineService = pipelineService;
    }

    @GetMapping
    @Operation(summary = "获取最新热搜")
    public ResponseEntity<HotSearchResult> getLatest() {
        return ResponseEntity.ok(hotSearchService.getLatestWithMeta());
    }

    @PostMapping("/trigger")
    @Operation(summary = "手动触发推送管线")
    public ResponseEntity<Map<String, String>> trigger() {
        pipelineService.runPipeline();
        return ResponseEntity.ok(Map.of("message", "管线已触发"));
    }

    @GetMapping("/history")
    @Operation(summary = "获取热搜历史快照列表")
    public ResponseEntity<List<Map<String, Object>>> getHistory(
            @RequestParam(defaultValue = "24") int hours) {
        int safeHours = Math.max(1, Math.min(hours, 168));
        return ResponseEntity.ok(hotSearchService.getHistorySnapshots(safeHours));
    }

    @GetMapping("/trend")
    @Operation(summary = "获取指定关键词的历史排名趋势")
    public ResponseEntity<List<Map<String, Object>>> getKeywordTrend(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "24") int hours) {
        int safeHours = Math.max(1, Math.min(hours, 168));
        return ResponseEntity.ok(hotSearchService.getKeywordTrend(keyword, safeHours));
    }
}
