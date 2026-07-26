package com.hotsearch.provider;

import com.hotsearch.dto.HotSearchItem;
import com.hotsearch.entity.Channel;
import com.hotsearch.service.WxNotificationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 小程序订阅消息通道。
 * 注意：该通道不参与管线的通用推送循环（PipelineService 中显式跳过），
 * 只经命中事件降噪路径（WxNotificationService）发送；此处的 send 仅服务于通道测试接口。
 */
@Component(WxSubscribeMessageProvider.PROVIDER_NAME)
public class WxSubscribeMessageProvider implements MessageProvider {

    public static final String PROVIDER_NAME = "wxsubscribe";

    private final WxNotificationService wxNotificationService;

    public WxSubscribeMessageProvider(@Lazy WxNotificationService wxNotificationService) {
        this.wxNotificationService = wxNotificationService;
    }

    @Override
    public void send(Channel channel, HotSearchItem primaryItem, List<HotSearchItem> allItems) {
        wxNotificationService.sendTest(channel);
    }
}
