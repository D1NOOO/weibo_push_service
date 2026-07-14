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
    interval-minutes: 10      # 默认抓取频率，可在系统配置页面动态修改
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
| 飞书 Webhook | `mode=webhook` + `webhookUrl` — 飞书群自定义机器人 Webhook 地址 |
| 飞书自建应用 | `mode=app` + `appId` + `appSecret` + `receiveId` + `receiveIdType` — 通过飞书应用机器人发送消息 |
| 钉钉 | `webhookUrl` — 钉钉机器人 Webhook 地址 |
| 企业微信 | `webhookUrl` — 企微机器人 Webhook 地址 |
| 微信机器人 | `apiBaseUrl` + `token` + `chat`；可选 `shortLinkEnabled` |
| Telegram | `token` + `chatId` — Bot Token 和 Chat ID |
| 通用 Webhook | `webhookUrl` — 任意 HTTP POST 端点 |

### 全局 Sink 短链

推荐将 [Sink](https://github.com/miantiao-me/Sink) 作为独立服务部署到 Cloudflare Workers，而不是把其源码集成进本项目。这样 Sink 的 KV、分析能力和发布周期与热搜服务解耦，本项目只通过服务端调用 `POST /api/link/create`。

1. 按 Sink 文档部署 Workers、绑定自定义域名，并配置 `NUXT_SITE_TOKEN`。
2. 在“系统配置 → Sink 短链服务”填写 `Sink Base URL`（例如 `https://s.20778888.xyz`）和与 `NUXT_SITE_TOKEN` 相同的 `Sink Site Token`。
3. 编辑任意推送通道，勾选“使用 Sink 短链接”。飞书、钉钉、企业微信、微信机器人、Telegram 和通用 Webhook 均支持。

仅当通道开关启用且全局 Sink 配置完整时才会缩短微博 URL。Sink 不可用或返回异常时，本次推送自动保留原始长链接，消息不会因短链服务故障而中断。旧版保存在微信通道中的 Sink 凭据会在启动时自动迁移到全局配置。

## Roadmap

详细产品规划见 [product-plan.md](./product-plan.md)。当前产品化方向是：以“微博热搜订阅提醒”为核心能力，后续承载在微信工具聚合小程序中；主服务负责抓取、匹配、去重和通知策略，微信云函数只作为发送小程序订阅消息的轻量钩子。

### P0：核心闭环

- 微信小程序登录：通过 `wx.login` 和主服务 `code2session` 建立 `openid -> userId`。
- 用户私有数据：订阅规则、通道配置、命中事件、通知日志按 `userId` 收口；热搜快照作为全局共享数据。
- 热搜订阅创建：支持关键词、标签过滤、最低热度和排除词。
- 命中事件聚合：新增 `match_events`，按 `userId + subscriptionId + keyword + activeWindow` 聚合，避免抓取频率越高命中次数越失真。
- 微信订阅消息：小程序端申请授权，主服务记录可发送额度，云函数负责调用微信订阅消息 API。
- 安全云函数钩子：主服务调用云函数时增加 shared secret、timestamp、nonce、signature 和防重放校验。
- 通知降噪：默认只在首次命中、标签升级、进入高排名或热度越过阈值时通知。

### P1：体验增强

- 订阅规则预览：创建规则时即时展示当前热搜可命中内容。
- 命中记录列表：展示今日新增、观察中、已通知、未通知原因。
- 通道管理增强：完善小程序订阅消息、飞书 Webhook、飞书自建应用、企微、钉钉、Telegram、通用 Webhook 的配置与测试体验。
- 抓取状态页：展示最近抓取时间、抓取条数、失败原因和下一次抓取时间。
- 简单工具广场：先放热搜提醒、天气、计算器、汇率、第三方小程序跳转等轻量入口。

### P2：后续扩展

- 完整工具生态与大量第三方小程序跳转。
- 团队/租户体系、复杂权限和操作审计。
- 报表中心、日报周报和高级趋势分析。
- 计费系统、AI 舆情分析、多平台 App。

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
