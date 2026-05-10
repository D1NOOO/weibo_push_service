# Weibo Hot Search Monitor

微博热搜监控与推送服务 — 定时抓取微博热搜榜，根据自定义订阅规则匹配后，通过飞书/钉钉/企微/Telegram 等渠道推送通知。

## 功能

- **定时抓取** 微博热搜榜，保存历史快照
- **灵活订阅** 支持关键词（含正则/前缀匹配）、排除词、标签过滤（爆/热/新）、最低热度阈值
- **多渠道推送** 飞书卡片消息、钉钉、企业微信、Telegram、通用 Webhook
- **批量推送** 一次匹配多条热搜合并为一条消息
- **智能去重** 可配置的去重窗口，避免重复推送
- **趋势查询** 关键词排名历史趋势
- **Web 管理界面** 管理订阅、通道、查看推送日志

## 技术栈

| 层 | 技术 |
|----|------|
| 框架 | Spring Boot 3.2 + Java 21 |
| 数据库 | SQLite + Hibernate (JPA) |
| 安全 | Spring Security + JWT (jjwt 0.12) + BCrypt |
| 前端 | 原生 HTML/CSS/JS + Chart.js v4 |
| 文档 | springdoc-openapi (Swagger UI) |
| 部署 | Docker + docker-compose |

## 快速开始

### Docker 部署

```bash
# 1. 设置 JWT 密钥
export JWT_SECRET=$(openssl rand -base64 32)

# 2. 构建并启动
docker compose up -d --build

# 3. 访问
# 管理界面: http://localhost:8080
# 默认账号: admin / admin123 (首次登录强制修改密码)
```

### 本地开发

```bash
# 需要 Java 21+ 和 Maven 3.9+
export JWT_SECRET=your-secret-key-at-least-32-chars
mvn spring-boot:run
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `JWT_SECRET` | 无（必填） | JWT 签名密钥，至少 32 字符 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | 数据库 schema 策略 |

## 配置说明

编辑 `application.yml`：

```yaml
app:
  schedule:
    cron: "0 0 * * * *"     # 抓取频率（默认每小时整点）
  dedupe:
    window-hours: 6          # 去重窗口（小时内同一关键词不重复推送）
  fetcher:
    user-agent: "..."        # 抓取请求的 UA
    # cookie: "SUB=xxx"      # 可选，微博 Cookie 避免 403
```

## 订阅规则

每条订阅支持以下过滤条件（所有条件 AND 逻辑）：

| 条件 | 说明 | 示例 |
|------|------|------|
| 关键词 | 空 = 匹配全部；支持 `prefix:XXX`、`regex:PATTERN` | `周杰伦`, `prefix:春晚`, `regex:.*演唱会` |
| 排除词 | 排除包含指定文本的热搜 | `广告` |
| 标签 | 仅匹配指定标签（爆/热/新等） | `爆`, `热` |
| 最低热度 | 低于此值不推送 | `500000` |

广告类热搜自动排除。

## 推送通道

| 通道 | 配置字段 |
|------|----------|
| 飞书 | `webhookUrl` — 飞书机器人 Webhook 地址 |
| 钉钉 | `webhookUrl` — 钉钉机器人 Webhook 地址 |
| 企业微信 | `webhookUrl` — 企微机器人 Webhook 地址 |
| Telegram | `token` + `chatId` — Bot Token 和 Chat ID |
| 通用 Webhook | `webhookUrl` — 任意 HTTP POST 端点 |

## API 文档

启动后访问 http://localhost:8080/swagger-ui.html 查看完整 API 文档。

主要端点：

```
GET    /api/hotsearch            最新热搜数据
POST   /api/hotsearch/trigger    手动触发推送管线
GET    /api/hotsearch/trend      关键词排名趋势
GET    /api/hotsearch/history    历史快照列表

GET    /api/subscriptions        我的订阅列表
POST   /api/subscriptions        创建订阅
PUT    /api/subscriptions/{id}   更新订阅
DELETE /api/subscriptions/{id}   删除订阅

GET    /api/channels             我的推送通道
POST   /api/channels             创建通道
PUT    /api/channels/{id}        更新通道
DELETE /api/channels/{id}        删除通道
POST   /api/channels/{id}/test   发送测试消息

GET    /api/delivery-logs        推送日志（按批次）
GET    /api/config               系统配置
PUT    /api/config               更新配置

POST   /api/auth/login           登录
POST   /api/auth/change-password 修改密码
```

## 项目结构

```
src/main/java/com/hotsearch/
├── HotsearchApplication.java
├── config/          # Security, RateLimiter, JWT filter
├── controller/      # REST API
├── dto/             # Request/Response records
├── entity/          # JPA entities
├── fetcher/         # Weibo hot search fetcher
├── matcher/         # Subscription matching engine
├── provider/        # Message providers (Feishu/Dingtalk/etc.)
├── repository/      # Spring Data JPA repositories
├── service/         # Business logic
└── util/            # JWT utilities
```
