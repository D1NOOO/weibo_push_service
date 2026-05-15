package com.hotsearch.provider;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;

import java.util.List;

public interface MessageProvider {
    void send(Channel channel, HotSearchItem primaryItem, List<HotSearchItem> allItems);

    /** Targets this provider will deliver to (e.g. chat names for wechat). Default: single null target. */
    default List<String> getTargets(Channel channel) {
        return List.of((String) null);
    }

    /** Send to a specific target. Default delegates to the target-agnostic send(). */
    default void send(Channel channel, HotSearchItem primaryItem, List<HotSearchItem> allItems, String target) {
        send(channel, primaryItem, allItems);
    }
}
