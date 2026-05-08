# 微博热搜监控服务

Spring Boot 3.x + SQLite 的微博热搜监控与推送服务。

## 功能
- 定时抓取微博热搜榜
- 按关键词/标签/热度值订阅过滤
- 飞书 Webhook 推送（交互卡片格式）
- 6小时去重窗口
- JWT 认证 + 管理后台

## 快速开始

```bash
# 构建
mvn clean package -DskipTests

# 运行
java -jar target/weibo-hotsearch-service-0.1.0.jar

# 或 Docker
docker-compose up --build
```

访问 http://localhost:8080 ，默认账号 admin / admin123

## API 文档
http://localhost:8080/swagger-ui.html

## 配置
编辑 `src/main/resources/application.yml`:
- 定时任务 cron 表达式
- 去重时间窗口
- JWT 密钥与过期时间
- 爬虫 User-Agent 和超时

## 技术栈
- Spring Boot 3.2 + Java 17 + Maven
- SQLite + Spring Data JPA
- jjwt (JWT) + BCrypt
- Jsoup (网页抓取)
- springdoc-openapi (Swagger)
