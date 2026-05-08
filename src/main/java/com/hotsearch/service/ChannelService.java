package com.hotsearch.service;

import com.hotsearch.dto.ChannelRequest;
import com.hotsearch.dto.ChannelResponse;
import com.hotsearch.entity.Channel;
import com.hotsearch.repository.ChannelRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChannelService {

    private final ChannelRepository channelRepository;

    public ChannelService(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    public List<ChannelResponse> listByUser(Long userId) {
        return channelRepository.findByUserId(userId).stream()
                .map(this::toResponse).toList();
    }

    public ChannelResponse create(Long userId, ChannelRequest req) {
        Channel ch = new Channel();
        ch.setUserId(userId);
        ch.setProvider(req.provider());
        ch.setConfigMap(req.config() != null ? req.config() : java.util.Map.of());
        ch.setEnabled(req.enabled() != null ? req.enabled() : true);
        return toResponse(channelRepository.save(ch));
    }

    public ChannelResponse update(Long userId, Long id, ChannelRequest req) {
        Channel ch = channelRepository.findById(id)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("通道不存在"));
        ch.setProvider(req.provider());
        ch.setConfigMap(req.config() != null ? req.config() : java.util.Map.of());
        ch.setEnabled(req.enabled());
        return toResponse(channelRepository.save(ch));
    }

    public void delete(Long userId, Long id) {
        Channel ch = channelRepository.findById(id)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("通道不存在"));
        channelRepository.delete(ch);
    }

    private ChannelResponse toResponse(Channel ch) {
        return new ChannelResponse(ch.getId(), ch.getProvider(), ch.getConfigMap(), ch.getEnabled(), ch.getCreatedAt());
    }

    public List<Channel> listAllEnabled() {
        return channelRepository.findByEnabledTrue();
    }
}
