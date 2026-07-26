package com.hotsearch.controller;

import com.hotsearch.config.ScheduleConfig;
import com.hotsearch.dto.HotSearchResult;
import com.hotsearch.service.ApplicationConfigService;
import com.hotsearch.service.HotSearchService;
import com.hotsearch.service.PipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hotsearch")
@Tag(name = "热搜数据", description = "热搜查询与手动触发")
public class HotSearchController {

    private final HotSearchService hotSearchService;
    private final PipelineService pipelineService;
    private final ApplicationConfigService configService;
    private final ZoneId scheduleZone;

    public HotSearchController(HotSearchService hotSearchService, PipelineService pipelineService,
                               ApplicationConfigService configService,
                               @Value("${app.schedule.zone:Asia/Shanghai}") String scheduleZone) {
        this.hotSearchService = hotSearchService;
        this.pipelineService = pipelineService;
        this.configService = configService;
        this.scheduleZone = ZoneId.of(scheduleZone);
    }

    @GetMapping("/status")
    @Operation(summary = "抓取状态", description = "最近抓取时间、条数、抓取间隔与下一次抓取时间")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> status = hotSearchService.getFetchStatus();
        int intervalMinutes = configService.getFetchIntervalMinutes();
        status.put("intervalMinutes", intervalMinutes);
        status.put("nextFetchAt", ScheduleConfig.nextExecution(Instant.now(), intervalMinutes, scheduleZone));
        return ResponseEntity.ok(status);
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
        if (keyword == null || keyword.isBlank()) {
            throw new RuntimeException("关键词不能为空");
        }
        if (keyword.length() > 200) {
            throw new RuntimeException("关键词长度不能超过200字符");
        }
        int safeHours = Math.max(1, Math.min(hours, 168));
        return ResponseEntity.ok(hotSearchService.getKeywordTrend(keyword.trim(), safeHours));
    }

}
