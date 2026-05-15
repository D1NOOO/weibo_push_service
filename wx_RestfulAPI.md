# WeChatBot RESTful API 接口文档

**版本**: 1.0.0  
**Base URL**: `http://localhost:5001`

---

## 鉴权

除 `GET /`、`GET /health`、`GET /api/health` 外，所有接口均需鉴权，支持两种方式：

**方式一：Query 参数**

```
?token=wechatbot-api-test-2026
```

**方式二：Bearer Header**

```
Authorization: Bearer wechatbot-api-test-2026
```

---

## 1. 消息发送

### 1.1 发送文字消息

**`POST /api/send/message`**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `chat` | string | 是 | 目标聊天名称（群聊名或联系人昵称/备注） |
| `message` | string | 是 | 消息文本内容 |
| `at_users` | string[] | 否 | 要 @ 的用户名列表，如 `["@张三"]` |
| `typing_delay` | bool | 否 | 是否模拟打字延迟，默认 `true` |
| `split_long` | bool | 否 | 是否自动拆分超长消息（>1500 字），默认 `true` |

**请求示例：**

```bash
curl -X POST "http://localhost:5001/api/send/message?token=wechatbot-api-test-2026" \
  -H "Content-Type: application/json" \
  -d '{"chat":"Feng","message":"你好，这是测试消息"}'
```

**群聊 @ 某人：**

```bash
curl -X POST "http://localhost:5001/api/send/message?token=wechatbot-api-test-2026" \
  -H "Content-Type: application/json" \
  -d '{"chat":"胖达测试群2026","message":"请注意查收","at_users":["张三"]}'
```

**成功响应：**

```json
{
    "success": true,
    "chat": "Feng",
    "message_id": "msg_1778780053_0",
    "message_ids": ["msg_1778780053_0"],
    "sent_count": 1,
    "total_count": 1,
    "timestamp": 1778780053.1147113
}
```

**失败响应：**

```json
{
    "success": false,
    "message": "缺少必要参数: chat, message"
}
```

---

### 1.2 发送图片/文件

**`POST /api/send/file`**

Content-Type: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `chat` | string | 是 | 目标聊天名称 |
| `file` | file | 是 | 文件二进制 |
| `file_type` | string | 否 | `image` / `document` / `video`，默认 `document` |

**请求示例 — 发送图片：**

```bash
curl -X POST "http://localhost:5001/api/send/file?token=wechatbot-api-test-2026" \
  -F "chat=Feng" \
  -F "file_type=image" \
  -F "file=@C:\Users\dino\Pictures\photo.png"
```

**请求示例 — 发送文件：**

```bash
curl -X POST "http://localhost:5001/api/send/file?token=wechatbot-api-test-2026" \
  -F "chat=胖达测试群2026" \
  -F "file_type=document" \
  -F "file=@D:\docs\report.pdf"
```

**成功响应：**

```json
{
    "success": true,
    "message_id": "file_1778780053",
    "chat": "Feng",
    "file_path": "C:\\Users\\dino\\Pictures\\photo.png",
    "file_type": "image",
    "timestamp": 1778780053.123
}
```

**失败响应：**

```json
{
    "success": false,
    "message": "文件不存在: C:\\Users\\dino\\Pictures\\photo.png"
}
```

---

### 1.3 批量发送

**`POST /api/send/batch`**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `messages` | array | 是 | 消息列表，每项包含 `chat` + `message` |
| `delay_between` | number | 否 | 消息间隔秒数，默认 `2` |
| `typing_delay` | bool | 否 | 模拟打字延迟，默认 `true` |

**请求示例：**

```bash
curl -X POST "http://localhost:5001/api/send/batch?token=wechatbot-api-test-2026" \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"chat": "Feng", "message": "消息1"},
      {"chat": "胖达测试群2026", "message": "消息2"}
    ],
    "delay_between": 2
  }'
```

**成功响应：**

```json
{
    "success": true,
    "total": 2,
    "succeeded": 2,
    "failed": 0,
    "results": [
        {"success": true, "chat": "Feng", "message_id": "msg_xxx_0"},
        {"success": true, "chat": "胖达测试群2026", "message_id": "msg_xxx_1"}
    ]
}
```

---

### 1.4 发送统计

**`GET /api/send/stats`**

```bash
curl "http://localhost:5001/api/send/stats?token=wechatbot-api-test-2026"
```

**响应：**

```json
{
    "success": true,
    "data": {
        "total_sent": 2,
        "success_count": 2,
        "failure_count": 0,
        "success_rate": "100.00%"
    }
}
```

**`POST /api/send/stats/reset`** — 重置统计

```bash
curl -X POST "http://localhost:5001/api/send/stats/reset?token=wechatbot-api-test-2026"
```

---

## 2. 监听管理

### 2.1 查询监听列表

**`GET /api/listen/list`**

```bash
curl "http://localhost:5001/api/listen/list?token=wechatbot-api-test-2026"
```

**响应：**

```json
{
    "success": true,
    "data": [
        {
            "name": "Feng",
            "type": "private",
            "prompt": "私信_胖达",
            "enable_forward": true,
            "enable_ai_reply": false,
            "status": "listening"
        },
        {
            "name": "胖达测试群2026",
            "type": "group",
            "prompt": "群聊_default",
            "enable_forward": true,
            "enable_ai_reply": false,
            "status": "listening"
        }
    ]
}
```

### 2.2 添加监听

**`POST /api/listen/add`**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 聊天名称 |
| `type` | string | 是 | `private` 或 `group` |
| `prompt` | string | 否 | 使用的 prompt 文件名（不含 .md 后缀） |
| `enable_forward` | bool | 否 | 是否启用消息转发，默认 `true` |
| `enable_ai_reply` | bool | 否 | 是否启用 AI 自动回复，默认 `false` |

### 2.3 移除监听

**`DELETE /api/listen/remove`**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 聊天名称 |

### 2.4 更新监听

**`PUT /api/listen/update`**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 聊天名称 |
| `type` | string | 否 | 聊天类型 |
| `prompt` | string | 否 | prompt 文件名 |
| `enable_forward` | bool | 否 | 是否转发 |
| `enable_ai_reply` | bool | 否 | 是否 AI 回复 |

---

## 3. 系统状态

### 3.1 健康检查（无需鉴权）

**`GET /health`** 或 **`GET /api/health`**

```bash
curl "http://localhost:5001/health"
```

```json
{
    "success": true,
    "data": {
        "healthy": false,
        "checks": {
            "wechat": true,
            "forward": false,
            "disk": true
        }
    }
}
```

### 3.2 运行状态

**`GET /api/status`**

```bash
curl "http://localhost:5001/api/status?token=wechatbot-api-test-2026"
```

```json
{
    "success": true,
    "data": {
        "status": "running",
        "wechat_connected": true,
        "listen_count": 4,
        "uptime": 3600,
        "uptime_str": "1小时0分0秒",
        "version": "3.28-api",
        "config": {
            "api_enabled": true,
            "api_port": 5001,
            "forward_enabled": false,
            "webhook_url": "http://localhost:5003/webhook"
        }
    }
}
```

### 3.3 消息统计

**`GET /api/stats`**

```bash
curl "http://localhost:5001/api/stats?token=wechatbot-api-test-2026"
```

---

## 4. Webhook 配置

### 4.1 查看配置

**`GET /api/config/webhook`**

```bash
curl "http://localhost:5001/api/config/webhook?token=wechatbot-api-test-2026"
```

### 4.2 设置配置

**`POST /api/config/webhook`**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | string | 否 | Webhook 接收地址 |
| `secret` | string | 否 | HMAC-SHA256 签名密钥 |
| `enabled` | bool | 否 | 是否启用转发 |

---

## 5. 媒体文件

### 5.1 下载文件

**`GET /api/media/download`**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 文件路径 |

### 5.2 预览文件

**`GET /api/media/preview`**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 文件路径（仅图片） |

### 5.3 文件信息

**`GET /api/media/info`**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 文件路径 |

---

## 6. 系统控制

### 6.1 重启 Bot

**`POST /api/control/restart`**

```bash
curl -X POST "http://localhost:5001/api/control/restart?token=wechatbot-api-test-2026"
```

### 6.2 停止 Bot

**`POST /api/control/stop`**

```bash
curl -X POST "http://localhost:5001/api/control/stop?token=wechatbot-api-test-2026"
```

### 6.3 清除对话上下文

**`POST /api/control/clear_context`**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `user_id` | string | 是 | 用户 ID |

### 6.4 清除记忆

**`POST /api/control/clear_memory`**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `user_id` | string | 是 | 用户 ID |
| `memory_type` | string | 否 | `temp` / `core` / `all`，默认 `temp` |

---

## 7. API 根路径（无需鉴权）

**`GET /`**

```bash
curl "http://localhost:5001/"
```

```json
{
    "name": "WeChatBot RESTful API",
    "version": "1.0.0",
    "status": "running",
    "endpoints": {
        "config": "/api/config/*",
        "listen": "/api/listen/*",
        "send": "/api/send/*",
        "status": "/api/*",
        "control": "/api/control/*",
        "health": "/api/health"
    }
}
```

---

## 错误码

| HTTP 状态码 | 说明 |
|-------------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 鉴权失败（token 无效或缺失） |
| 404 | 端点不存在 |
| 500 | 服务端内部错误 |

鉴权失败响应：

```json
{
    "success": false,
    "message": "未授权访问：无效或缺失的API令牌"
}
```
