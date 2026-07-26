package com.hotsearch.config;

import com.hotsearch.exception.ApiException;
import com.hotsearch.exception.BusinessException;
import com.hotsearch.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionKeepsItsStatusAndMessage() {
        ResponseEntity<Map<String, Object>> notFound = handler.handleApi(new NotFoundException("通道不存在"));
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody()).containsEntry("message", "通道不存在");

        ResponseEntity<Map<String, Object>> badRequest = handler.handleApi(new BusinessException("参数不合法"));
        assertThat(badRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> tooMany = handler.handleApi(
                new ApiException(HttpStatus.TOO_MANY_REQUESTS, "太频繁"));
        assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void unexpectedExceptionsAreHiddenBehindGeneric500() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleUnknown(new IllegalStateException("内部实现细节不应外泄"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "服务器内部错误");
    }
}
