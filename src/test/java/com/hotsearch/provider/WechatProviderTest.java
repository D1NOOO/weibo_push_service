package com.hotsearch.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotsearch.dto.HotSearchItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WechatProviderTest {

    @Test
    void rendersUrlsPreparedByTheDeliveryPipeline() {
        WechatProvider provider = new WechatProvider(new ObjectMapper());
        HotSearchItem item = new HotSearchItem(1, "世界杯", "热", 1_998_000L, false,
                "https://s.20778888.xyz/abcde");

        String message = provider.buildMessage(List.of(item), "世界杯热搜提醒");

        assertThat(message).contains("🔗https://s.20778888.xyz/abcde");
    }
}
