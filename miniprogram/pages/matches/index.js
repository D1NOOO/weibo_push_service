const api = require("../../utils/api");
const format = require("../../utils/format");
const app = getApp();

const TABS = [
  { key: "all", text: "全部" },
  { key: "today", text: "今日" },
  { key: "notified", text: "已通知" },
  { key: "silent", text: "未通知" }
];

Page({
  data: {
    tabs: TABS,
    activeTab: "all",
    summary: null,
    allItems: [],
    items: [],
    loaded: false
  },

  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh());
  },

  load() {
    return app
      .ensureLogin()
      .then(() => api.listMatches(168))
      .then((summary) => {
        const items = (summary.items || []).map((item) => {
          const status = format.deliveryStatus(item.deliveryStatus);
          return {
            ...item,
            statusText: status.text,
            statusType: status.type,
            labelClass: format.labelClass(item.latestLabel),
            hotText: format.hotValue(item.latestHotValue),
            maxHotText: format.hotValue(item.maxHotValue),
            firstSeenText: format.shortTime(item.firstSeenAt),
            lastSeenText: format.relativeTime(item.lastSeenAt),
            todayFlag: format.isToday(item.firstSeenAt),
            reasonText: this.reasonText(item.notifyReason)
          };
        });
        this.setData({ summary, allItems: items, loaded: true });
        this.applyFilter();
      })
      .catch((err) => {
        wx.showToast({ title: err.message || "加载失败", icon: "none" });
      });
  },

  reasonText(reason) {
    if (!reason) return "";
    const map = {
      NEW_EVENT: "首次命中",
      LABEL_UPGRADE: "标签升级",
      RANK_TOP: "进入前排",
      HOT_SURGE: "热度激增"
    };
    return reason
      .split(",")
      .map((r) => map[r.trim()] || r.trim())
      .join("·");
  },

  switchTab(event) {
    this.setData({ activeTab: event.currentTarget.dataset.key });
    this.applyFilter();
  },

  goTrend(event) {
    const keyword = event.currentTarget.dataset.keyword;
    if (!keyword) return;
    wx.navigateTo({ url: `/pages/trend/index?keyword=${encodeURIComponent(keyword)}` });
  },

  applyFilter() {
    const { activeTab, allItems } = this.data;
    let items = allItems;
    if (activeTab === "today") {
      items = allItems.filter((item) => item.todayFlag);
    } else if (activeTab === "notified") {
      items = allItems.filter((item) => item.deliveryStatus === "SENT");
    } else if (activeTab === "silent") {
      items = allItems.filter((item) => item.deliveryStatus !== "SENT");
    }
    this.setData({ items });
  }
});
