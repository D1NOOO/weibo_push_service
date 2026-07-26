const PROVIDER_NAMES = {
  wxsubscribe: "小程序通知",
  feishu: "飞书",
  dingtalk: "钉钉",
  wecom: "企业微信",
  telegram: "Telegram",
  webhook: "通用 Webhook",
  wechat: "微信 Bot"
};

const DELIVERY_STATUS = {
  SENT: { text: "已通知", type: "success" },
  FAILED: { text: "发送失败", type: "danger" },
  NO_QUOTA: { text: "额度不足", type: "warn" },
  NO_CHANNEL: { text: "未开提醒", type: "muted" },
  NONE: { text: "观察中", type: "muted" }
};

function providerName(provider) {
  return PROVIDER_NAMES[provider] || provider || "未知通道";
}

function deliveryStatus(status) {
  return DELIVERY_STATUS[status] || DELIVERY_STATUS.NONE;
}

/** 后端时间格式 "yyyy-MM-dd HH:mm:ss" -> Date（兼容 iOS） */
function parseTime(value) {
  if (!value) return null;
  const normalized = String(value).replace(" ", "T");
  const date = new Date(normalized);
  return isNaN(date.getTime()) ? null : date;
}

function pad(n) {
  return n < 10 ? `0${n}` : `${n}`;
}

function shortTime(value) {
  const date = parseTime(value);
  if (!date) return "-";
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function relativeTime(value) {
  const date = parseTime(value);
  if (!date) return "-";
  const diffMs = Date.now() - date.getTime();
  if (diffMs < 0) return shortTime(value);
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} 天前`;
  return shortTime(value);
}

function hotValue(value) {
  if (value === null || value === undefined || value === "") return "-";
  const num = Number(value);
  if (isNaN(num)) return String(value);
  if (num >= 100000000) return `${(num / 100000000).toFixed(1)}亿`;
  if (num >= 10000) return `${(num / 10000).toFixed(1)}万`;
  return String(num);
}

function isToday(value) {
  const date = parseTime(value);
  if (!date) return false;
  const now = new Date();
  return date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate();
}

/** 热搜标签 -> 样式类后缀（避免 WXSS 中文类名） */
function labelClass(label) {
  if (label === "爆" || label === "沸") return "boom";
  if (label === "热") return "hot";
  if (label === "新") return "new";
  return "plain";
}

module.exports = {
  providerName,
  deliveryStatus,
  shortTime,
  relativeTime,
  hotValue,
  isToday,
  labelClass
};
