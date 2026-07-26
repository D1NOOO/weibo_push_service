package com.hotsearch.provider;

import com.hotsearch.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 推送提供者上游调用失败，映射为 502。 */
public class ProviderException extends ApiException {

    public ProviderException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public ProviderException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
