package com.hotsearch.provider;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;

import java.util.List;

public interface MessageProvider {
    void send(Channel channel, HotSearchItem primaryItem, List<HotSearchItem> allItems);
}
