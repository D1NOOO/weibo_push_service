# Handoff — 微博热搜提醒小程序适配

> 本文件记录关键节点进展与决策，供接手者快速上手。按时间倒序追加「进展日志」。

## 目标

在现有微博热搜监控服务（Spring Boot 主服务）基础上，完成微信小程序适配的 P0 闭环：

```
微信登录 -> 创建热搜订阅 -> 授权订阅消息 -> 主服务定时抓取/匹配/聚合 -> 命中后按降噪策略通知 -> 小程序查看命中记录
```

架构约束（来自需求与 product-plan.md §6/§14）：

- 小程序前端与主服务通过**固定域名** HTTPS 通信（`wx.request`），主服务部署在自有服务器。
- 微信云函数**只作为订阅消息发送钩子**，调用次数只与实际发送的微信提醒条数成正比（不随抓取频率放大），确保在云开发免费额度内。
- 主服务保留全部业务逻辑：用户、订阅、抓取、匹配、命中聚合、去重、通知策略、外部通道分发。

## 关键决策

| # | 决策 | 理由 |
|---|------|------|
| 1 | 以 `referer/src`（2026-07 版）覆盖本仓库 `src/`（2026-05 版）作为后端基线 | referer 是主项目最新工作副本，多两个月修复（配置持久化、快照清理、短链、测试等）；diff 确认无本仓库独有后端改动 |
| 2 | 主服务调用云函数支持两种模式：`http-trigger`（云开发 HTTP 访问服务公网 URL，默认）与 `openapi`（invokecloudfunction + access_token） | http-trigger 模式服务器无需维护 access_token / IP 白名单；公网入口以 HMAC 签名 + 时间戳 + nonce 防重放保护 |
| 3 | 签名基于**原始 JSON 字符串** `message` 字段（`HMAC-SHA256(secret, "timestamp.nonce.messageString")`） | 避免 Java/Node 两侧 JSON 重序列化字节不一致导致验签失败 |
| 4 | 小程序订阅消息作为一种通道 `provider=wxsubscribe`（channels 表一行），但**不走**通用推送循环，只走命中事件降噪路径 | 复用订阅-通道绑定/日志/启停/测试能力；避免每次去重命中都消耗珍贵的一次性授权额度 |
| 5 | 降噪策略只作用于 wxsubscribe 通道；外部通道（飞书/钉钉/企微/TG/Webhook）保持原有去重窗口行为 | 保护既有用户行为；订阅消息额度稀缺才需要事件级降噪 |
| 6 | match_events 聚合：`userId+subscriptionId+normalizedKeyword` 在活跃窗口（默认 6h）内 upsert，窗口外新建事件；MVP 不做 match_observations | 按 product-plan §7 |
| 7 | 通知触发条件：首次命中 / 标签升级为爆·沸 / 排名进入前 N（默认 3）/ 热度较此前峰值增长超倍率（默认 2.0） | product-plan §8 |
| 8 | MySQL 走 `prod` profile（`application-prod.yml` + docker-compose.prod.yml），dev 默认仍 SQLite；生产建表暂用 `ddl-auto=update`（可用 `DDL_AUTO=validate` 收紧），Flyway 后置 | 首次部署零迁移成本；计划 §15.1 的 Flyway 待后端 schema 稳定后引入 |
| 9 | 微信用户复用 `users` 表：新增可空 `openid`(unique)/`unionid`/`nickname`/`avatar`/`last_login_at`，微信用户生成随机 `username`+随机密码哈希 | 不动现有管理端账号密码登录 |

## 当前状态

- [x] 现状勘察与 referer 后端基线同步（`cp -r referer/src/. src/`，含测试目录与新版 Web 静态资源）
- [x] 后端：微信登录 `/api/auth/wx-login`（code2session）+ `/api/auth/me` + User 扩展（openid/unionid/nickname/avatar/lastLoginAt）
- [x] 后端：match_events 聚合（MatchEventService，活跃窗口 upsert + 4 类通知原因）+ `/api/match-events`
- [x] 后端：notification_quota + `/api/wx/subscribe-message/{quota,grant}`（grant 自动创建/启用 wxsubscribe 通道；43101 清零、失败回补）
- [x] 后端：WxCloudFunctionClient（HMAC 原始串签名，http-trigger/openapi 双模式）+ WxNotificationService + wxsubscribe 通道（通用循环跳过，仅事件降噪路径）+ delivery_logs.user_id
- [x] 后端：MySQL prod profile（application-prod.yml + docker-compose.prod.yml + mysql-connector-j）；compose 增加 TZ=Asia/Shanghai
- [x] 云函数：sendSubscribeMessage 验签/时间窗/nonce 防重放/双事件形态（HTTP 触发 body 与直接调用）+ config.json openapi 权限 + 部署 README
- [x] 小程序：config.js（按 envVersion 切域名）+ 静默登录/401 重试 + 五个页面全量实现
- [x] 构建与测试：Docker maven（aliyun 镜像源）；首轮 19 个既有测试全绿；新增 MatchEventServiceTest（6 例）+ SmokeContextTest（Spring 上下文 + SQLite 建表冒烟）
- [x] 文档：README（部署清单/环境变量/新 API）、miniprogram/README、云函数 README、product-plan §15.4 实现状态全部更新

## P1 体验增强（product-plan §10）

- [x] P1-1 订阅规则预览：`POST /api/subscriptions/preview`（试跑最新快照，不落库）+ 编辑页「预览命中」按钮与结果列表
- [x] P1-2 命中记录列表：P0 已实现（今日新增/观察中/已通知/未通知原因 + 筛选 tab）
- [x] P1-3 通道管理完善：Web 管理端适配 wxsubscribe（显示名、说明文案、隐藏编辑入口防误改 provider、保留测试/启停/删除）；小程序端已有测试/启停
- [x] P1-4 抓取状态：`GET /api/hotsearch/status`（进程内状态 + 快照兜底 + 下次抓取时间）+「我的」页展示
- [x] P1-5 工具广场：首页网格入口（热搜订阅/命中记录/热搜趋势/计算器 + config.js 可配第三方跳转位）；新增热搜趋势页（复用 /api/hotsearch/trend，命中列表可直达）与本地计算器页；天气/汇率按 plan 定位为第三方跳转位，不自研

## 用户待办（上线前需要你操作的事项）

> 📘 **新手请直接看 [DEPLOY.md](./DEPLOY.md)**：按「本地跑通 → 云函数提醒 → 服务器上线 → 发布」四个阶段 step by step 展开，每步带控制台路径、可复制命令、验证方法和常见问题排查表。下面的 A/B/C/D 清单是同一内容的速查版。

以下事项涉及你的微信账号、云开发控制台和服务器，代码侧已全部就绪，照单执行即可。

### A. 小程序后台（mp.weixin.qq.com）

- [ ] A1. 获取小程序 **AppID / AppSecret**（开发管理 → 开发设置），并把 AppID 填入根目录 `project.config.json` 的 `appid` 字段（当前是 `touristappid`）。
- [ ] A2. 申请**订阅消息模板**（功能 → 订阅消息 → 我的模板）。推荐选带这四类字段的模板：关键词(`thing`)、排名(`character_string`)、热度(`number`)、时间(`time`)。记下**模板 ID** 和每个字段的 key（如 `thing1`/`character_string2`…）。
- [ ] A3. 把主服务生产域名（HTTPS）加入 **request 合法域名**（开发管理 → 开发设置 → 服务器域名）。
- [ ] A4. 若走 openapi 调用模式才需要：把服务器出口 IP 加入 **IP 白名单**（默认 http-trigger 模式不需要）。

### B. 云开发控制台（微信开发者工具内）

- [ ] B1. 开通云开发，记下**环境 ID**。
- [ ] B2. 右键 `cloudfunctions/sendSubscribeMessage` → **上传并部署（云端安装依赖）**。
- [ ] B3. 生成一个强随机串作为共享密钥（如 `openssl rand -hex 32`），配置到云函数**环境变量** `SUBSCRIBE_MESSAGE_SHARED_SECRET`。
- [ ] B4. 云开发控制台 → **HTTP 访问服务** → 开通并新建路径（如 `/sendSubscribeMessage`）绑定该云函数，记下完整 URL：`https://<环境ID>.service.tcloudbase.com/sendSubscribeMessage`。

### C. 服务器

- [ ] C1. 配置 HTTPS 反向代理（Nginx/Caddy）：你的固定域名 → `127.0.0.1:28080`（docker-compose.prod.yml 暴露 28080）。
- [ ] C2. 在仓库目录准备环境变量（`.env` 或 export）：
  ```bash
  JWT_SECRET=<至少32字符>
  MYSQL_PASSWORD=<数据库密码>  MYSQL_ROOT_PASSWORD=<root密码>
  WX_APPID=<A1>  WX_SECRET=<A1>
  WX_SUBSCRIBE_TEMPLATE_ID=<A2 模板ID>
  WX_CLOUD_SHARED_SECRET=<B3 同一个串>
  WX_CLOUD_HTTP_TRIGGER_URL=<B4 的 URL>
  # 体验版联调期间建议: WX_MINIPROGRAM_STATE=trial
  ```
- [ ] C3. `docker compose -f docker-compose.prod.yml up -d --build`
- [ ] C4. 若 A2 模板字段 key 与默认映射（keyword→thing1, rank→character_string2, hotValue→number3, time→time4）不一致，通过环境变量覆盖，例如：
  ```bash
  SPRING_APPLICATION_JSON='{"app":{"wx":{"subscribe":{"field-mapping":{"keyword":"thing2","rank":"character_string1","hotValue":"number4","time":"time3"}}}}}'
  ```

### D. 小程序端与联调

- [ ] D1. `miniprogram/config.js` 中 `PROD_API_BASE_URL` 改为你的生产域名。
- [ ] D2. 真机联调顺序：开发者工具打开仓库根目录 → 首页自动登录 → 新建订阅（可用「预览命中」验证规则）→ 「我的」页开启提醒（授权 +1）→ 「我的」页对小程序通知通道点**测试**（应收到一条订阅消息，消耗 1 次额度）→ 再开启提醒补额度 → 等定时抓取或 Web 管理端手动触发管线 → 命中后收到提醒。
- [ ] D3.（可选）工具广场第三方跳转位：在 `miniprogram/config.js` 的 `THIRD_PARTY_TOOLS` 填入天气/汇率等目标小程序的 `appId`（和可选 `path`），首页工具广场自动出现入口。

## 已知取舍与后续建议

- 生产建表暂用 Hibernate `ddl-auto=update`（`DDL_AUTO=validate` 可收紧）；Flyway 迁移管理待 schema 稳定后引入（plan §15.1 的后置项）。
- 降噪策略只作用于 wxsubscribe：外部通道（飞书等）保持原有「去重窗口」行为，与 referer 线上行为一致。
- 云函数 nonce 防重放为实例内存级（冷启动清空），依赖 ±5 分钟时间窗兜底；如需更强防重放可改用云数据库存 nonce。
- wx-login 与账号登录共用 RateLimiter（5 次/分钟/IP，key 前缀区分）；大量用户共享出口 IP 时需调大。
- 升级已有 SQLite 数据：所有新列可空、新表自动创建，`users.openid` 唯一索引允许多 NULL，直接滚动升级即可。

## 部署所需环境变量（实现后生效）

| 变量 | 用途 |
|------|------|
| `JWT_SECRET` | JWT 签名（已有） |
| `WX_APPID` / `WX_SECRET` | 小程序 code2session |
| `WX_SUBSCRIBE_TEMPLATE_ID` | 订阅消息模板 ID |
| `WX_CLOUD_SHARED_SECRET` | 主服务 ↔ 云函数 HMAC 共享密钥（云函数侧配 `SUBSCRIBE_MESSAGE_SHARED_SECRET`） |
| `WX_CLOUD_INVOKE_MODE` | `http-trigger`（默认）或 `openapi` |
| `WX_CLOUD_HTTP_TRIGGER_URL` | http-trigger 模式：云开发 HTTP 访问服务绑定到云函数的完整 URL |
| `WX_CLOUD_ENV_ID` | openapi 模式：云开发环境 ID |

## 进展日志

- **2026-07-27 (5)** 新增新手向部署指南 **DEPLOY.md**（四阶段：本地跑通→云函数提醒→服务器上线→发布；每步含控制台路径/命令/验证/排错表 Q1-Q10 与小词典），README 与本文件已挂链接；compose 透传 `APP_FETCHER_COOKIE`、`SPRING_APPLICATION_JSON`。全部非隐私文件提交并推送至 `microprogram` 分支（.env/referer//data/ 已被 .gitignore 排除）。
- **2026-07-27 (4)** P1 体验增强全部完成并回归通过（**27/27 tests，BUILD SUCCESS**）：新增订阅规则预览（后端 preview 接口 + 编辑页 UI + 单测）、抓取状态（status 接口 +「我的」页卡片）、Web 管理端 wxsubscribe 通道适配、工具广场（首页网格 + 热搜趋势页 + 计算器页 + config.js 第三方跳转位）、命中列表直达趋势。用户待办清单（A/B/C/D 共 14 项）已录入本文件上方章节。P2（团队体系/报表/计费/AI 舆情等）按 plan 保持后置。
- **2026-07-27 (3)** 验证与收尾完成：Docker maven 全量测试 **26/26 通过**（含新增 MatchEventServiceTest 6 例、SmokeContextTest 上下文冒烟——验证全部新 Bean 装配与新实体 SQLite 建表）。product-plan §15.4 更新为已完成状态。改动规模：38 个文件修改 + 29 个新文件，未提交（等待用户确认后自行提交）。下一步：按 README「小程序上线检查清单」做真机联调（真实 AppID、订阅消息模板、云函数部署与 HTTP 访问服务 URL）。
- **2026-07-27 (2)** P0 闭环代码全部完成：后端 5 个模块（wx 登录 / match_events / 额度 / 云函数客户端+通知编排 / prod profile）、云函数重写、小程序 5 页面全量实现。README 与云函数 README 补齐部署说明。referer/ 加入 .gitignore（含 .env 与运行数据，不入库）。首轮 Docker maven 构建 BUILD SUCCESS（19 tests 全绿），新增 MatchEventServiceTest + SmokeContextTest 复跑中。
- **2026-07-27 (1)** 勘察完成：确认 referer 后端已具备订阅/通道/推送日志的 userId 隔离与账号体系；缺口为微信登录、命中事件、额度、云函数链路、小程序页面。将 referer/src 同步为本仓库后端基线。制定上表决策 1–9，开始实现。
