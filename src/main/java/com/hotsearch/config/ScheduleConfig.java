package com.hotsearch.config;

import com.hotsearch.service.ApplicationConfigService;
import com.hotsearch.service.PipelineService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Configuration
public class ScheduleConfig implements SchedulingConfigurer {

    private final PipelineService pipelineService;
    private final ApplicationConfigService configService;
    private final ZoneId scheduleZone;

    public ScheduleConfig(PipelineService pipelineService, ApplicationConfigService configService,
                          @Value("${app.schedule.zone:Asia/Shanghai}") String scheduleZone) {
        this.pipelineService = pipelineService;
        this.configService = configService;
        this.scheduleZone = ZoneId.of(scheduleZone);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(pipelineService::scheduledRun, triggerContext -> {
            Instant now = Instant.now();
            return nextExecution(now, configService.getFetchIntervalMinutes(), scheduleZone);
        });
    }

    static Instant nextExecution(Instant now, int intervalMinutes, ZoneId zone) {
        LocalDateTime localEpoch = LocalDate.of(1970, 1, 1).atStartOfDay();
        LocalDateTime current = LocalDateTime.ofInstant(now, zone);
        long elapsedMinutes = Duration.between(localEpoch, current).toMinutes();
        long nextSlot = (elapsedMinutes / intervalMinutes + 1) * intervalMinutes;
        return localEpoch.plusMinutes(nextSlot).atZone(zone).toInstant();
    }
}
