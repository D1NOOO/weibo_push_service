package com.hotsearch.provider;

import com.hotsearch.dto.HotSearchItem;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

    static String buildPlainText(List<HotSearchItem> items, String title) {
        StringBuilder text = new StringBuilder();
        text.append(normalizeTitle(title)).append("\n\n");

        for (int i = 0; i < items.size(); i++) {
            HotSearchItem item = items.get(i);
            text.append(i + 1).append(": ").append(item.keyword());
            if (item.label() != null) text.append(" [").append(item.label()).append("]");
            if (item.hotValue() != null) text.append(" 热度").append(formatHeat(item.hotValue()));
            text.append(" 排名#").append(item.rank());
            if (item.isAd()) text.append(" (广告)");
            if (item.url() != null && !item.url().isBlank()) {
                text.append("\n🔗").append(item.url());
            }
            text.append("\n");
            if (i < items.size() - 1) text.append("\n");
        }
        return text.toString();
    }
}
