package com.hotsearch.exception;

import org.springframework.http.HttpStatus;

/** 目标资源不存在或不属于当前用户，映射为 404。 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
