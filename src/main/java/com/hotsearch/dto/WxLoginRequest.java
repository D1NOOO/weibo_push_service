package com.hotsearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WxLoginRequest(
        @NotBlank(message = "code不能为空") String code,
        @Size(max = 64, message = "昵称过长") String nickname,
        @Size(max = 1024, message = "头像地址过长") String avatar
) {}
