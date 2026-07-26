const api = require("../../utils/api");
const format = require("../../utils/format");
const app = getApp();

Page({
  data: {
    items: [],
    loading: false,
    loaded: false
  },

  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh());
  },

  load() {
    this.setData({ loading: true });
    return app
      .ensureLogin()
      .then(() => api.listSubscriptions())
      .then((items) => {
        this.setData({
          items: (items || []).map((item) => ({
            ...item,
            keywordsText: Array.isArray(item.keywords) && item.keywords.length
              ? item.keywords.join("、")
              : "匹配全部热搜",
            labelsText: Array.isArray(item.labels) && item.labels.length
              ? item.labels.join(" ")
              : "",
            channelCount: Array.isArray(item.channelIds) ? item.channelIds.length : 0,
            createdText: format.shortTime(item.createdAt)
          })),
          loaded: true
        });
      })
      .catch((err) => {
        wx.showToast({ title: err.message || "加载失败", icon: "none" });
      })
      .finally(() => this.setData({ loading: false }));
  },

  onToggle(event) {
    const { id } = event.currentTarget.dataset;
    const enabled = event.detail.value;
    api
      .setSubscriptionEnabled(id, enabled)
      .then(() => wx.showToast({ title: enabled ? "已启用" : "已停用", icon: "none" }))
      .catch((err) => {
        wx.showToast({ title: err.message || "操作失败", icon: "none" });
        this.load();
      });
  },

  goEdit(event) {
    const { id } = event.currentTarget.dataset;
    wx.navigateTo({ url: `/pages/subscription-edit/index?id=${id}` });
  },

  goCreate() {
    wx.navigateTo({ url: "/pages/subscription-edit/index" });
  }
});
