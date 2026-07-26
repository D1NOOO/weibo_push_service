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
| 小程序 | 微信原生小程序 |
| 云函数 | 微信云函数，用于订阅消息发送钩子 |
| 文档 | springdoc-openapi (Swagger UI) |
| 部署 | Docker + docker-compose |

## 快速开始

### 微信小程序开发

用微信开发者工具打开仓库根目录。根目录的 `project.config.json` 已配置：

```text
miniprogramRoot = miniprogram/
cloudfunctionRoot = cloudfunctions/
```

小程序 P0 闭环与 P1 体验增强已实现：微信静默登录、订阅管理（含规则预览）、命中事件列表（含趋势跳转）、订阅消息授权与额度、热搜榜首页、抓取状态、工具广场（热搜订阅/命中记录/热搜趋势/计算器 + 可配置第三方小程序跳转位）。

架构分工（详见 [product-plan.md](./product-plan.md)）：

```text
小程序  --HTTPS 固定域名-->  主服务（自有服务器：抓取/匹配/聚合/降噪/通知策略）
主服务  --HMAC 签名------>  云函数 sendSubscribeMessage（仅发送微信订阅消息）
```

云函数调用次数只与实际发送的微信提醒条数成正比（命中事件降噪后才发送），与抓取频率无关，正常使用远低于云开发免费额度。

新手部署请看 **[DEPLOY.md](./DEPLOY.md)**（四阶段 step by step：本地跑通 → 云函数提醒 → 服务器上线 → 发布，含验证方法与排错表）。

小程序上线检查清单（速查版）：

1. `project.config.json` 中 `appid` 换成自己的小程序 AppID。
2. [miniprogram/config.js](./miniprogram/config.js) 中 `PROD_API_BASE_URL` 改为主服务 HTTPS 域名，并在小程序后台加入 request 合法域名。
3. 小程序后台「订阅消息」申请模板（推荐字段：关键词 thing、排名 character_string、热度 number、时间 time），模板 ID 配到主服务 `WX_SUBSCRIBE_TEMPLATE_ID`；字段 key 不一致时用 `app.wx.subscribe.field-mapping` 调整。
4. 部署云函数并配置共享密钥，见 [cloudfunctions/sendSubscribeMessage/README.md](./cloudfunctions/sendSubscribeMessage/README.md)。
5. 主服务配置 `WX_APPID` / `WX_SECRET` / `WX_CLOUD_*` 环境变量（见下表）。

生产部署（MySQL）：

```bash
export JWT_SECRET=... MYSQL_PASSWORD=... MYSQL_ROOT_PASSWORD=... WX_APPID=... WX_SECRET=...
export WX_SUBSCRIBE_TEMPLATE_ID=... WX_CLOUD_SHARED_SECRET=... WX_CLOUD_HTTP_TRIGGER_URL=...
docker compose -f docker-compose.prod.yml up -d --build
```

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
| `SPRING_PROFILES_ACTIVE` | 无 | `prod` 时启用 MySQL（见 application-prod.yml） |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` | - | prod profile 的 MySQL 连接信息 |
| `WX_APPID` / `WX_SECRET` | 空 | 小程序 AppID/Secret，用于 code2session（微信登录必填） |
| `WX_SUBSCRIBE_TEMPLATE_ID` | 空 | 订阅消息模板 ID（微信提醒必填） |
| `WX_MINIPROGRAM_STATE` | `formal` | 订阅消息跳转的小程序版本：developer/trial/formal |
| `WX_CLOUD_INVOKE_MODE` | `http-trigger` | 云函数调用方式：`http-trigger` 或 `openapi` |
| `WX_CLOUD_HTTP_TRIGGER_URL` | 空 | http-trigger 模式：HTTP 访问服务绑定云函数的完整 URL |
| `WX_CLOUD_ENV_ID` | 空 | openapi 模式：云开发环境 ID（需服务器 IP 白名单） |
| `WX_CLOUD_SHARED_SECRET` | 空 | 主服务↔云函数 HMAC 共享密钥（云函数侧为 `SUBSCRIBE_MESSAGE_SHARED_SECRET`） |

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
| 飞书 Webhook | `mode=webhook` + `webhookUrl` — 飞书群自定义机器人 Webhook 地址 |
| 飞书自建应用 | `mode=app` + `appId` + `appSecret` + `receiveId` + `receiveIdType` — 通过飞书应用机器人发送消息 |
| 钉钉 | `webhookUrl` — 钉钉机器人 Webhook 地址 |
| 企业微信 | `webhookUrl` — 企微机器人 Webhook 地址 |
| Telegram | `token` + `chatId` — Bot Token 和 Chat ID |
| 通用 Webhook | `webhookUrl` — 任意 HTTP POST 端点 |

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

POST   /api/auth/login           登录（Web 管理端账号密码）
POST   /api/auth/wx-login        微信小程序登录（code2session）
GET    /api/auth/me              当前用户信息
POST   /api/auth/change-password 修改密码

GET    /api/match-events                  命中事件列表（含今日新增/活跃统计）
GET    /api/wx/subscribe-message/quota    订阅消息额度与模板 ID
POST   /api/wx/subscribe-message/grant    上报订阅消息授权（额度 +1）
POST   /api/subscriptions/preview         订阅规则预览（试跑当前热搜，不落库）
GET    /api/hotsearch/status              抓取状态（最近抓取/条数/间隔/下次时间）
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

miniprogram/         # 微信小程序端
cloudfunctions/      # 微信云函数
```
