package com.hotsearch.provider;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;

import java.util.List;

/**
 * 一次推送请求。
 *
 * @param channel 目标通道
 * @param items   待推送条目，非空，第一条为主条目
 * @param target  投递目标（如微信聊天名）；无多目标概念的 provider 为 null
 * @param title   消息标题（通常为订阅规则名），可为 null
 */
public record PushMessage(Channel channel, List<HotSearchItem> items, String target, String title) {

    public HotSearchItem primaryItem() {
        return items.get(0);
    }
}
