package com.hotsearch.provider;

import com.hotsearch.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 通道配置缺失或非法（用户可自行修复），映射为 400。 */
public class ProviderConfigException extends ApiException {

    public ProviderConfigException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
