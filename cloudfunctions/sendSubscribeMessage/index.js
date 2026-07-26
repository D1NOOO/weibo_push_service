/**
 * 微信订阅消息发送云函数（主服务安全钩子）。
 *
 * 只做一件事：校验主服务签名后调用 cloud.openapi.subscribeMessage.send。
 * 抓取/匹配/去重/降噪全部在主服务完成，云函数调用次数只与实际发送的提醒条数成正比。
 *
 * 载荷格式（两种调用方式的 body/event 相同）：
 *   { timestamp, nonce, signature, message }
 *   - message 为原始 JSON 字符串（避免跨语言重序列化差异）
 *   - signature = HMAC-SHA256(SUBSCRIBE_MESSAGE_SHARED_SECRET, `${timestamp}.${nonce}.${message}`) 的 hex
 *
 * 支持的调用方式：
 *   1. 云开发「HTTP 访问服务」：event 为 { httpMethod, body, ... }，payload 在 body 中
 *   2. invokecloudfunction / callFunction：event 即 payload 本身
 *
 * 环境变量：SUBSCRIBE_MESSAGE_SHARED_SECRET（与主服务 WX_CLOUD_SHARED_SECRET 一致）
 */
const cloud = require("wx-server-sdk");
const crypto = require("crypto");

cloud.init({
  env: cloud.DYNAMIC_CURRENT_ENV
});

const MAX_CLOCK_SKEW_MS = 5 * 60 * 1000;

// 进程内 nonce 防重放缓存（尽力而为：冷启动会清空，配合时间窗口使用）
const seenNonces = new Map();

function pruneNonces(now) {
  for (const [nonce, ts] of seenNonces) {
    if (now - ts > MAX_CLOCK_SKEW_MS * 2) {
      seenNonces.delete(nonce);
    }
  }
}

function extractPayload(event) {
  // HTTP 访问服务：payload 在 event.body（可能 base64）
  if (event && typeof event.httpMethod === "string") {
    let body = event.body || "";
    if (event.isBase64Encoded) {
      body = Buffer.from(body, "base64").toString("utf8");
    }
    try {
      return JSON.parse(body);
    } catch (e) {
      return null;
    }
  }
  return event || null;
}

function verifyPayload(payload) {
  const secret = process.env.SUBSCRIBE_MESSAGE_SHARED_SECRET;
  if (!secret) {
    return "SUBSCRIBE_MESSAGE_SHARED_SECRET is not configured";
  }
  if (!payload) {
    return "Invalid payload";
  }

  const { timestamp, nonce, signature, message } = payload;
  if (!timestamp || !nonce || !signature || typeof message !== "string") {
    return "Missing signature fields";
  }

  const now = Date.now();
  const ts = Number(timestamp);
  if (!Number.isFinite(ts) || Math.abs(now - ts) > MAX_CLOCK_SKEW_MS) {
    return "Invalid timestamp";
  }

  pruneNonces(now);
  if (seenNonces.has(nonce)) {
    return "Replayed nonce";
  }

  const expected = crypto
    .createHmac("sha256", secret)
    .update(`${timestamp}.${nonce}.${message}`)
    .digest("hex");
  const actual = Buffer.from(String(signature));
  const expectedBuffer = Buffer.from(expected);
  if (actual.length !== expectedBuffer.length || !crypto.timingSafeEqual(actual, expectedBuffer)) {
    return "Invalid signature";
  }

  seenNonces.set(nonce, now);
  return null;
}

exports.main = async (event) => {
  const payload = extractPayload(event);
  const verifyError = verifyPayload(payload);
  if (verifyError) {
    return { ok: false, errcode: 401, errmsg: verifyError };
  }

  let message;
  try {
    message = JSON.parse(payload.message);
  } catch (e) {
    return { ok: false, errcode: 400, errmsg: "message is not valid JSON" };
  }

  if (!message.openid || !message.templateId) {
    return { ok: false, errcode: 400, errmsg: "openid and templateId are required" };
  }

  try {
    const result = await cloud.openapi.subscribeMessage.send({
      touser: message.openid,
      templateId: message.templateId,
      page: message.page || "pages/matches/index",
      data: message.data || {},
      miniprogramState: message.miniprogramState || "formal",
      lang: message.lang || "zh_CN"
    });
    return { ok: true, errcode: 0, result };
  } catch (err) {
    // wx-server-sdk 的 openapi 错误带 errCode/errMsg
    const errcode = typeof err.errCode === "number" ? err.errCode : -1;
    const errmsg = err.errMsg || err.message || "subscribeMessage.send failed";
    return { ok: false, errcode, errmsg };
  }
};
