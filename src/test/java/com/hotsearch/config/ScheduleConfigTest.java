package com.hotsearch.config;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleConfigTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void alignsFiveMinuteIntervalToWallClock() {
        Instant now = Instant.parse("2026-07-20T08:07:54Z");

        assertThat(ScheduleConfig.nextExecution(now, 5, SHANGHAI))
                .isEqualTo(Instant.parse("2026-07-20T08:10:00Z"));
    }

    @Test
    void exactBoundarySchedulesTheFollowingBoundary() {
        Instant now = Instant.parse("2026-07-20T08:10:00Z");

        assertThat(ScheduleConfig.nextExecution(now, 5, SHANGHAI))
                .isEqualTo(Instant.parse("2026-07-20T08:15:00Z"));
    }

    @Test
    void dailyIntervalAlignsToLocalMidnight() {
        Instant now = Instant.parse("2026-07-20T15:59:00Z");

        assertThat(ScheduleConfig.nextExecution(now, 1_440, SHANGHAI))
                .isEqualTo(Instant.parse("2026-07-20T16:00:00Z"));
    }

    @Test
    void intervalRemainsContinuousAcrossLocalMidnight() {
        Instant beforeMidnight = Instant.parse("1970-01-01T15:59:30Z");

        assertThat(ScheduleConfig.nextExecution(beforeMidnight, 7, SHANGHAI))
                .isEqualTo(Instant.parse("1970-01-01T16:02:00Z"));
    }
}
