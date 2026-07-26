package com.hotsearch.exception;

import org.springframework.http.HttpStatus;

/**
 * 携带 HTTP 状态码的业务异常基类，由 GlobalExceptionHandler 统一转换为 JSON 错误响应。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
