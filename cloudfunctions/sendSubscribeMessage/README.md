# sendSubscribeMessage 云函数

主服务 → 微信订阅消息的安全发送钩子。只校验签名并调用 `cloud.openapi.subscribeMessage.send`，不含任何业务逻辑（是否通知、额度扣减、日志均在主服务）。

## 部署步骤

1. 微信开发者工具中开通云开发，记下环境 ID。
2. 右键 `cloudfunctions/sendSubscribeMessage` → 上传并部署（云端安装依赖）。
3. 云开发控制台 → 云函数 → sendSubscribeMessage → 配置 → 环境变量：
   - `SUBSCRIBE_MESSAGE_SHARED_SECRET`：随机长字符串，与主服务 `WX_CLOUD_SHARED_SECRET` 保持一致。

## 主服务调用方式（二选一）

### 方式 A：HTTP 访问服务（推荐，主服务无需 access_token 与 IP 白名单）

1. 云开发控制台 → 更多 → HTTP 访问服务 → 新建，路径如 `/sendSubscribeMessage` 绑定本函数。
2. 主服务配置 `WX_CLOUD_INVOKE_MODE=http-trigger`、`WX_CLOUD_HTTP_TRIGGER_URL=https://<env-id>.service.tcloudbase.com/sendSubscribeMessage`。
3. 该 URL 公网可达，安全性依赖 HMAC 签名 + 时间戳窗口（±5 分钟）+ nonce 防重放，请务必使用强随机共享密钥。

### 方式 B：invokecloudfunction OpenAPI

1. 主服务配置 `WX_CLOUD_INVOKE_MODE=openapi`、`WX_CLOUD_ENV_ID=<env-id>`。
2. 需要 `WX_APPID`/`WX_SECRET` 获取 access_token，且服务器出口 IP 必须加入小程序后台「IP 白名单」。

## 载荷与签名

```json
{ "timestamp": 1753600000000, "nonce": "hex", "signature": "hmac-hex", "message": "{...json string...}" }
```

`signature = HMAC-SHA256(secret, "timestamp.nonce.message")`，对 message **原始字符串**签名（避免 Java/Node JSON 重序列化差异），验签通过后才 `JSON.parse(message)`。

message 内容：`openid`、`templateId`、`page`、`data`、`miniprogramState`、`lang`。

## 返回

- 成功：`{ "ok": true, "errcode": 0, "result": {...} }`
- 失败：`{ "ok": false, "errcode": <wx错误码或401/400>, "errmsg": "..." }`
  - `43101`：用户无有效订阅授权（主服务据此清零本地额度）
  - `401`：验签失败 / 时间戳超窗 / nonce 重放
