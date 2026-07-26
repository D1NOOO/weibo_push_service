# 部署指南（新手向，Step by Step）

> 本指南假设你从未部署过微信小程序。跟着做即可，每一步都有「怎么验证」。
> 勾选进度也同步维护在 [handoff.md](./handoff.md) 的「用户待办」章节。

## 总览：一共四个阶段

| 阶段 | 内容 | 耗时 | 需要花钱吗 |
|------|------|------|-----------|
| 阶段 1 | 本地跑通（电脑上后端 + 开发者工具） | ~30 分钟 | 免费 |
| 阶段 2 | 云函数 + 订阅消息（微信提醒能收到） | ~1 小时 | 云开发套餐（见 2.1 说明） |
| 阶段 3 | 服务器上线（固定域名 HTTPS） | 半天（备案另计 1~2 周） | 服务器 + 域名费用 |
| 阶段 4 | 发布（体验版 → 审核 → 正式版） | 1~3 天（审核时间） | 免费 |

阶段 1、2 都不需要服务器，先在自己电脑上把全部功能玩通，再上服务器。

---

## 阶段 0：准备（一次性）

### 0.1 注册小程序账号

1. 浏览器打开 https://mp.weixin.qq.com → 右上角「立即注册」→ 选「小程序」。
2. 用一个**没注册过公众号/小程序**的邮箱注册，邮箱验证 → 主体选「个人」（免费，够用）→ 用你的微信扫码绑定管理员。
3. 注册完成后登录小程序后台，进入「开发 → 开发管理 → 开发设置」，看到两样东西：
   - **AppID(小程序ID)**：形如 `wx1234567890abcdef`，复制保存。
   - **AppSecret(小程序密钥)**：点「生成」或「重置」，**只显示一次**，立刻复制保存到安全的地方。
4. 顺手填一下「设置 → 基本设置」里的小程序名称/头像/简介（审核需要）。

> 名词解释：AppID 是小程序的身份证号（可公开）；AppSecret 是密码（**绝不能提交到 git 或发给别人**）。

### 0.2 安装微信开发者工具

1. 下载「稳定版」：https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html
2. 安装后用你的微信扫码登录。

### 0.3 电脑上装好 Docker Desktop

你机器上已有（`docker --version` 能出版本号即可）。后端构建、运行全靠它，不需要装 Java/Maven。

---

## 阶段 1：本地跑通（不需要服务器/域名/云函数）

目标：电脑上后端跑起来，开发者工具里能登录、建订阅、看到命中。

### 1.1 启动本地后端

1. 在仓库根目录（`weibo_wx_microprogram/`）新建一个名为 `.env` 的文件（已被 .gitignore 排除，不会被提交），内容：

   ```bash
   JWT_SECRET=随便一串至少32个字符的英文数字
   WX_APPID=wx开头的你的AppID
   WX_SECRET=你的AppSecret
   ```

   生成随机串的办法：打开 Git Bash 执行 `openssl rand -hex 32`，把输出粘进去。

2. 在仓库根目录打开终端（PowerShell 即可），执行：

   ```bash
   docker compose up -d --build
   ```

   第一次会下载依赖构建镜像，需要几分钟。

3. **验证**：浏览器打开 http://localhost:8080 ，出现登录页 → 用 `admin` / `admin123` 登录 → 首次会强制修改密码，改一个记得住的。

4. 在管理端仪表盘点击「**刷新数据**」按钮，让后端立刻抓取一次微博热搜。**验证**：页面上出现热搜列表。
   - 如果热搜是空的，多半是微博接口 403，见文末[常见问题 Q1](#常见问题)。

### 1.2 打开小程序项目

1. 微信开发者工具 → 「导入项目」→ 目录选**仓库根目录**（不是 miniprogram 子目录）→ AppID 填你自己的（工具会写入 `project.config.json`）→ 后端服务选「不使用云服务」也没关系（阶段 2 再开）。
2. 进入项目后：右上角「详情」→「本地设置」→ 勾选 ✅「**不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书**」。
   - 原因：正式环境小程序只能请求 HTTPS 备案域名，本地 `http://127.0.0.1:8080` 只有勾了这个才允许。
3. 点「编译」。

4. **验证**：模拟器里首页应显示三个统计卡片（今日命中/观察中/提醒额度）和「当前热搜」列表——这说明 `wx.login → /api/auth/wx-login → JWT` 整条登录链路通了。
   - 如果显示「未连接主服务」，点「重新登录」，并看下方[常见问题 Q2](#常见问题)。

### 1.3 建一条订阅，验证命中

1. 首页点「新建热搜订阅」→ 名称随便填（如 `测试`）→ **关键词留空**（留空=匹配全部热搜，最容易命中）→ 点「**预览命中**」，应显示当前热搜可命中几十条 → 「保存订阅」。
2. 回到浏览器管理端，再点一次「刷新数据」（触发抓取+匹配管线）。
3. **验证**：小程序切到「命中」tab，下拉刷新，应出现一批命中事件，徽章显示「未开提醒」（因为还没开订阅消息，正常）。

到这里 P0 数据闭环已通。⏸️ 可以休息，下个阶段再搞微信提醒。

---

## 阶段 2：云函数 + 订阅消息（收到微信提醒）

目标：手机微信「服务通知」里收到热搜提醒。

### 2.1 开通云开发

1. 开发者工具顶部工具栏点「**云开发**」按钮 → 首次会引导开通 → 创建环境（名字随意，如 `hotsearch`）。
2. 记下**环境 ID**（形如 `hotsearch-3g1a2b3c4d5e6f`，云开发控制台顶部可见）。

> 💰 费用说明：微信云开发目前是套餐制（基础套餐约 19.9 元/月，含每月大量云函数调用/流量配额，以控制台实际标价为准）。本项目架构下云函数**只在真正发微信提醒时被调用一次**，与抓取频率无关，用量极小、远低于套餐配额。如果你不想为云开发付费，告诉我，我可以给主服务加一个「直连模式」（不经云函数、直接调微信订阅消息 API，完全免费，只需服务器 IP 加白名单）。

### 2.2 申请订阅消息模板

1. 小程序后台 → 「功能（或 广告与服务）→ 订阅消息」→「公共模板库」→ 搜「**提醒**」。
2. 挑一个字段接近这样的模板（字段类型看括号里的前缀）：
   - 某个 `thing` 字段 → 放关键词
   - 某个 `character_string` 或 `number` 字段 → 放排名
   - 某个 `number` 字段 → 放热度
   - 某个 `time` 字段 → 放时间
   选用后在「我的模板」里点开详情，**记下模板 ID** 和**每个字段的 key**（如 `thing1`、`time4`，注意数字后缀）。
3. 字段 key 如果与默认映射不一致（默认：关键词→`thing1`、排名→`character_string2`、热度→`number3`、时间→`time4`），后面 2.5 步用环境变量改，先记着。
   - 模板字段比 4 个少也没关系：映射里对应项留空即不发送该字段。

### 2.3 部署云函数

1. 开发者工具左侧文件树找到 `cloudfunctions/sendSubscribeMessage` → 右键 →「**上传并部署：云端安装依赖**」→ 等待完成提示。
2. 生成共享密钥（Git Bash：`openssl rand -hex 32`），保存好，两边都要用。
3. 云开发控制台 → 「云函数」→ 点 `sendSubscribeMessage` → 「配置」→「环境变量」→ 新增：
   - 键：`SUBSCRIBE_MESSAGE_SHARED_SECRET`，值：刚生成的密钥 → 保存。

### 2.4 开通 HTTP 访问服务

1. 云开发控制台 → 找到「**HTTP 访问服务**」（一般在「环境 → 其他」或左侧菜单里）→ 开通。
2. 「新建」路由：路径填 `/sendSubscribeMessage`，关联资源选云函数 `sendSubscribeMessage` → 确定。
3. 记下完整地址：`https://<你的环境ID>.service.tcloudbase.com/sendSubscribeMessage`。
4. **验证**：Git Bash 执行（应返回验签失败——这恰好说明函数已可访问、验签在工作）：
   ```bash
   curl -X POST https://<环境ID>.service.tcloudbase.com/sendSubscribeMessage -d '{}'
   # 期望返回: {"ok":false,"errcode":401,...}
   ```

### 2.5 本地后端接上云函数

1. 编辑仓库根目录 `.env`，追加：

   ```bash
   WX_SUBSCRIBE_TEMPLATE_ID=2.2记下的模板ID
   WX_CLOUD_SHARED_SECRET=2.3生成的密钥
   WX_CLOUD_HTTP_TRIGGER_URL=https://<环境ID>.service.tcloudbase.com/sendSubscribeMessage
   WX_MINIPROGRAM_STATE=developer
   ```

   若 2.2 的字段 key 和默认不同，再追加一行（按你的实际 key 改）：

   ```bash
   SPRING_APPLICATION_JSON={"app":{"wx":{"subscribe":{"field-mapping":{"keyword":"thing2","rank":"character_string1","hotValue":"number4","time":"time3"}}}}}
   ```

   > `SPRING_APPLICATION_JSON` 和 `APP_FETCHER_COOKIE` 两个 compose 文件都已透传，写进 `.env` 即生效。

2. 重启后端：`docker compose up -d --build`（改了 .env 要重建容器才生效：`docker compose down; docker compose up -d`）。

### 2.6 授权并测试

1. 开发者工具点「预览」，用**手机**扫码打开小程序（订阅消息发到手机微信，模拟器收不到）。
2. 手机上：「我的」→「开启提醒（授权 +1 次）」→ 弹窗选**允许**。
3. 「我的」→ 通知通道里会出现「小程序通知」→ 点「**测试**」。
4. **验证**：手机微信「服务通知」收到一条测试提醒 ✅（内容是"测试热搜提醒"）。
   - 失败的话看 toast 里的错误码，对照[常见问题 Q4/Q5](#常见问题)。
5. 完整闭环验证：再点几次「开启提醒」攒 2~3 次额度 → 建一条容易命中的订阅 → 管理端「刷新数据」→ 手机收到真实热搜提醒，「命中」列表徽章变「已通知」。

到这里，全部功能在本地已 100% 跑通。

---

## 阶段 3：服务器上线

目标：后端跑在你的服务器上，固定 HTTPS 域名，手机不依赖你电脑。

### 3.0 前置：域名 + 备案（最耗时，尽早启动）

- 你需要一个**已 ICP 备案**的域名——小程序 request 合法域名硬性要求 HTTPS + 备案。
- 域名在阿里云/腾讯云购买（几十元/年），备案在购买处提交（服务器也要在国内），全程 1~2 周。
- 备案期间不影响阶段 1/2 的本地玩法。
- 域名解析：DNS 控制台加一条 **A 记录**，主机记录如 `hot`（最终域名 `hot.你的域名.com`），记录值填服务器公网 IP。

### 3.1 服务器装 Docker

SSH 登录服务器（Ubuntu/Debian 为例）：

```bash
curl -fsSL https://get.docker.com | sh
sudo systemctl enable --now docker
docker --version && docker compose version   # 都能出版本号即可
```

### 3.2 上传代码并配置

```bash
# 服务器上
git clone <你的仓库地址> weibo_hotsearch && cd weibo_hotsearch
cp /dev/null .env && nano .env    # 或 vim
```

`.env` 内容（把值换成你自己的；前四个密码/密钥类都可用 `openssl rand -hex 32` 生成）：

```bash
JWT_SECRET=...
MYSQL_PASSWORD=...
MYSQL_ROOT_PASSWORD=...
WX_CLOUD_SHARED_SECRET=与云函数环境变量一致的那个
WX_APPID=...
WX_SECRET=...
WX_SUBSCRIBE_TEMPLATE_ID=...
WX_CLOUD_HTTP_TRIGGER_URL=https://<环境ID>.service.tcloudbase.com/sendSubscribeMessage
WX_MINIPROGRAM_STATE=trial
```

### 3.3 启动

```bash
docker compose -f docker-compose.prod.yml up -d --build
# 看日志确认启动成功（出现 "Started HotsearchApplication" 字样）
docker compose -f docker-compose.prod.yml logs -f weibo-hotsearch
```

**验证**：`curl http://127.0.0.1:28080/api/health` 返回正常 JSON。

### 3.4 HTTPS 反向代理（推荐 Caddy，自动搞定证书）

```bash
# Ubuntu 安装 Caddy（官方源）
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install caddy

# 配置：把域名换成你的
sudo tee /etc/caddy/Caddyfile > /dev/null <<'EOF'
hot.你的域名.com {
    reverse_proxy 127.0.0.1:28080
}
EOF
sudo systemctl reload caddy
```

Caddy 会自动申请并续期 HTTPS 证书（前提：域名已解析到本机、80/443 端口对外开放——云服务器记得在安全组放行 80、443）。

**验证**：本地电脑浏览器打开 `https://hot.你的域名.com`，出现管理端登录页（登录后立即改掉 admin123）。

### 3.5 小程序指向生产域名

1. 小程序后台 → 开发管理 → 开发设置 → 服务器域名 → **request 合法域名** → 添加 `https://hot.你的域名.com`（每月修改次数有限，别频繁改）。
2. 编辑 `miniprogram/config.js`：`PROD_API_BASE_URL = "https://hot.你的域名.com"`。
3. 开发者工具重新编译 → 手机「预览」（这次**不勾**「不校验合法域名」也应该能用）→ 重复 2.6 的验证。

---

## 阶段 4：发布

1. **传体验版**：开发者工具右上角「上传」→ 填版本号（如 `0.1.0`）和备注 → 小程序后台「管理 → 版本管理 → 开发版本」→ 「选为体验版」→ 生成体验二维码；「成员管理」里把要试用的微信号加为体验成员。此阶段服务器保持 `WX_MINIPROGRAM_STATE=trial`。
2. **提交审核**：版本管理 → 提交审核。类目建议选工具类（如 工具-效率）。
   - ⚠️ 涉及热点资讯聚合的小程序审核有不确定性；若被驳回，把功能描述侧重为「自定义关键词提醒工具」再试，或把驳回原因发给我。
3. **发布正式版**：审核通过后点「发布」。服务器 `.env` 把 `WX_MINIPROGRAM_STATE` 改为 `formal`，`docker compose -f docker-compose.prod.yml up -d` 重启生效。

---

## 常见问题

| # | 症状 | 原因与解决 |
|---|------|-----------|
| Q1 | 管理端「刷新数据」后热搜为空 | 微博接口 403。浏览器登录 weibo.com → F12 → Network 任意请求的 Cookie 里复制 `SUB=xxx` 段 → `.env` 加 `APP_FETCHER_COOKIE=SUB=xxx`（compose 已透传）→ 重启容器 |
| Q2 | 小程序「未连接主服务」 | ① 后端没起来：`docker compose ps` 看状态；② 没勾「不校验合法域名」（本地）；③ `.env` 里 WX_APPID/WX_SECRET 不对 → 报「AppID 配置错误」；④ AppSecret 重置过导致失效 |
| Q3 | 登录报「code 无效」(40029) | 点太快重复用了 code，重试即可；频繁出现说明 AppSecret 与 AppID 不配套 |
| Q4 | 通道测试报 `errcode=47003` | 模板字段 key 与映射不一致。对照 2.2 记的字段 key 配 `SPRING_APPLICATION_JSON` 的 field-mapping |
| Q5 | 通道测试报 `43101` | 用户没授权或额度已用完，「我的」页重新「开启提醒」再测 |
| Q6 | 通道测试报 `401 Invalid signature` | 云函数环境变量 `SUBSCRIBE_MESSAGE_SHARED_SECRET` 与服务端 `WX_CLOUD_SHARED_SECRET` 不一致（注意别带引号/空格），改完云函数配置无需重新部署，服务端要重启 |
| Q7 | curl 云函数 URL 返回 404 | HTTP 访问服务路由路径与 URL 不一致，回 2.4 核对 |
| Q8 | 手机收不到订阅消息但接口成功 | 微信「设置-通知」被关；或授权时选了「不允许」；每授权 1 次只能发 1 条，额度归零后要重新授权 |
| Q9 | prod 启动失败 MySQL 相关 | `.env` 缺 `MYSQL_PASSWORD`/`MYSQL_ROOT_PASSWORD`；改过密码需删除 `data/mysql` 目录重来（会清库） |
| Q10 | 时间显示差 8 小时 | 容器时区未生效，确认用的是本仓库 compose 文件（已设 `TZ=Asia/Shanghai`）并重建容器 |

## 小词典

- **openid**：用户在你这个小程序里的唯一 ID，后端用它把微信用户对应到数据库账号。
- **code2session**：小程序 `wx.login` 拿到临时 code，后端拿 code + AppSecret 找微信换 openid 的过程。
- **订阅消息**：微信唯一的合规「推送」方式；用户点一次「允许」，你才能发一条。所以本项目做了降噪：只有首次命中/标签升级/进前排/热度激增才消耗额度。
- **云函数 / 云开发**：微信提供的托管代码环境。本项目只用它发订阅消息，业务全在你自己服务器。
- **HTTP 访问服务**：给云函数一个公网 URL，让你的服务器能直接 POST 调用它（用 HMAC 签名防别人乱调）。
- **反向代理**：Caddy/Nginx 站在 443 端口，把 HTTPS 流量转给本机 28080 的后端，并负责证书。
- **ICP 备案**：国内网站/接口域名的登记手续，小程序合法域名的硬性前提。
