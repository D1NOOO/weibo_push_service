package com.hotsearch.provider;

import com.hotsearch.entity.Channel;

import java.util.Collections;
import java.util.List;

/**
 * 推送提供者：一个实现对应一种推送渠道，Bean 名称即通道的 provider 标识。
 */
public interface MessageProvider {

    /**
     * 该通道要投递到的目标列表（如微信通道配置的多个聊天名）。
     * 无多目标概念的 provider 使用默认实现，返回单个 null 目标；
     * 返回空列表表示通道缺少必要的目标配置，调用方应记录失败而非静默跳过。
     */
    default List<String> getTargets(Channel channel) {
        return Collections.singletonList(null);
    }

    /**
     * 向单个目标发送一条推送。
     *
     * @throws ProviderConfigException 通道配置缺失或非法
     * @throws RateLimitedException    上游限频，可退避重试
     * @throws ProviderException       其他上游失败
     */
    void send(PushMessage message);
}
