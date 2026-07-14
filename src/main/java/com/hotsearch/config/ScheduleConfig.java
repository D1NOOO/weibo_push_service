package com.hotsearch.config;

import com.hotsearch.service.ApplicationConfigService;
import com.hotsearch.service.PipelineService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;
import java.time.Instant;

@Configuration
public class ScheduleConfig implements SchedulingConfigurer {

    private final PipelineService pipelineService;
    private final ApplicationConfigService configService;

    public ScheduleConfig(PipelineService pipelineService, ApplicationConfigService configService) {
        this.pipelineService = pipelineService;
        this.configService = configService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(pipelineService::scheduledRun, triggerContext -> {
            Instant lastCompletion = triggerContext.lastCompletion();
            Instant base = lastCompletion == null ? Instant.now() : lastCompletion;
            return base.plus(Duration.ofMinutes(configService.getFetchIntervalMinutes()));
        });
    }
}
