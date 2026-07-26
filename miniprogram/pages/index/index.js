const api = require("../../utils/api");
const format = require("../../utils/format");
const config = require("../../config");
const app = getApp();

const BUILTIN_TOOLS = [
  { key: "subscribe", name: "热搜订阅", icon: "订" },
  { key: "matches", name: "命中记录", icon: "中" },
  { key: "trend", name: "热搜趋势", icon: "势" },
  { key: "calculator", name: "计算器", icon: "算" }
];

Page({
  data: {
    loggedIn: false,
    loginError: "",
    quota: null,
    summary: null,
    hotItems: [],
    hotFetchedAt: "",
    loading: false,
    tools: []
  },

  onLoad() {
    const thirdParty = (config.thirdPartyTools || [])
      .filter((tool) => tool && tool.appId)
      .map((tool, i) => ({
        key: `mp_${i}`,
        name: tool.name || "小程序",
        icon: tool.icon || (tool.name ? tool.name[0] : "跳"),
        appId: tool.appId,
        path: tool.path || ""
      }));
    this.setData({ tools: BUILTIN_TOOLS.concat(thirdParty) });
  },

  onToolTap(event) {
    const key = event.currentTarget.dataset.key;
    if (key === "subscribe") {
      wx.switchTab({ url: "/pages/subscriptions/index" });
      return;
    }
    if (key === "matches") {
      wx.switchTab({ url: "/pages/matches/index" });
      return;
    }
    if (key === "trend") {
      wx.navigateTo({ url: "/pages/trend/index" });
      return;
    }
    if (key === "calculator") {
      wx.navigateTo({ url: "/pages/calculator/index" });
      return;
    }
    const tool = this.data.tools.find((t) => t.key === key);
    if (tool && tool.appId) {
      wx.navigateToMiniProgram({
        appId: tool.appId,
        path: tool.path || undefined,
        fail(err) {
          if (err.errMsg && err.errMsg.indexOf("cancel") < 0) {
            wx.showToast({ title: "跳转失败", icon: "none" });
          }
        }
      });
    }
  },

  onShow() {
    this.refresh();
  },

  onPullDownRefresh() {
    this.refresh().finally(() => wx.stopPullDownRefresh());
  },

  refresh() {
    this.setData({ loading: true });
    return app
      .ensureLogin()
      .then(() => {
        this.setData({ loggedIn: true, loginError: "" });
        return Promise.all([this.loadQuota(), this.loadSummary(), this.loadHotSearch()]);
      })
      .catch((err) => {
        this.setData({ loggedIn: false, loginError: err.message || "无法连接主服务" });
      })
      .finally(() => this.setData({ loading: false }));
  },

  retryLogin() {
    this.setData({ loginError: "" });
    app
      .ensureLogin(true)
      .then(() => this.refresh())
      .catch((err) => {
        this.setData({ loggedIn: false, loginError: err.message || "无法连接主服务" });
        wx.showToast({ title: err.message || "登录失败", icon: "none" });
      });
  },

  loadQuota() {
    return api
      .getSubscribeQuota()
      .then((quota) => this.setData({ quota }))
      .catch(() => this.setData({ quota: null }));
  },

  loadSummary() {
    return api
      .listMatches(24)
      .then((summary) => {
        const items = (summary.items || []).slice(0, 3).map((item) => ({
          ...item,
          lastSeenText: format.relativeTime(item.lastSeenAt),
          hotText: format.hotValue(item.latestHotValue),
          labelClass: format.labelClass(item.latestLabel)
        }));
        this.setData({ summary: { ...summary, items } });
      })
      .catch(() => this.setData({ summary: null }));
  },

  loadHotSearch() {
    return api
      .getLatestHotSearch()
      .then((res) => {
        const items = (res.items || []).slice(0, 10).map((item) => ({
          ...item,
          hotText: format.hotValue(item.hotValue),
          labelClass: format.labelClass(item.label),
          topRank: item.rank >= 1 && item.rank - 3 <= 0
        }));
        this.setData({
          hotItems: items,
          hotFetchedAt: res.fetchedAt ? format.relativeTime(res.fetchedAt) : ""
        });
      })
      .catch(() => this.setData({ hotItems: [], hotFetchedAt: "" }));
  },

  goCreate() {
    wx.navigateTo({ url: "/pages/subscription-edit/index" });
  },

  goMatches() {
    wx.switchTab({ url: "/pages/matches/index" });
  },

  goProfile() {
    wx.switchTab({ url: "/pages/profile/index" });
  }
});
