package com.hotsearch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 冒烟测试：验证全部 Bean 装配（含微信登录、命中事件、额度、云函数客户端）
 * 与 Hibernate 实体映射能在 SQLite 上完整启动。
 */
@SpringBootTest(properties = {
        "jwt.secret=smoke-test-secret-key-at-least-32-characters",
        "spring.datasource.url=jdbc:sqlite:target/smoke-test.db"
})
class SmokeContextTest {

    @Test
    void contextLoads() {
    }
}
