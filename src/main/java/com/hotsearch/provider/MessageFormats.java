package com.hotsearch.provider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** provider 共用的文案格式化工具。 */
final class MessageFormats {

    /** 消息内展示时间统一使用北京时间（业务面向微博中文用户），与服务器时区无关。 */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private MessageFormats() {
    }

    static String normalizeTitle(String title) {
        return title == null ? "" : title.trim();
    }

    static String formatHeat(long hotValue) {
        if (hotValue >= 10000) return String.format("%.1f万", hotValue / 10000.0);
        return String.valueOf(hotValue);
    }

    static String shortDisplayTime() {
        return LocalDateTime.now(DISPLAY_ZONE).format(SHORT_TIME);
    }
}
