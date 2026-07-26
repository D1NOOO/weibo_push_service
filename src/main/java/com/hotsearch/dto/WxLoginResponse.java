package com.hotsearch.dto;

public record WxLoginResponse(
        String token,
        Long userId,
        String username,
        String nickname,
        String avatar,
        boolean newUser
) {}
