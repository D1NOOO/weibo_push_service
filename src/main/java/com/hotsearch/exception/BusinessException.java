package com.hotsearch.exception;

import org.springframework.http.HttpStatus;

/** 请求本身不合法或违反业务规则，映射为 400。 */
public class BusinessException extends ApiException {

    public BusinessException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
