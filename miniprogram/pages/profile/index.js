const api = require("../../utils/api");
const format = require("../../utils/format");
const app = getApp();

Page({
  data: {
    loggedIn: false,
    user: null,
    quota: null,
    channels: [],
    fetchStatus: null,
    apiBaseUrl: "",
    granting: false
  },

  onShow() {
    this.setData({ apiBaseUrl: app.globalData.apiBaseUrl });
    this.refresh();
  },

  onPullDownRefresh() {
    this.refresh().finally(() => wx.stopPullDownRefresh());
  },

  refresh() {
    return app
      .ensureLogin()
      .then(() => {
        this.setData({ loggedIn: true, user: app.globalData.user });
        return Promise.all([this.loadQuota(), this.loadChannels(), this.loadFetchStatus()]);
      })
      .catch(() => {
        this.setData({ loggedIn: false, user: null, quota: null, channels: [], fetchStatus: null });
      });
  },

  loadFetchStatus() {
    return api
      .getFetchStatus()
      .then((status) => {
        this.setData({
          fetchStatus: {
            ...status,
            lastText: status.lastSuccessAt ? format.relativeTime(status.lastSuccessAt) : "暂无",
            nextText: status.nextFetchAt ? format.shortTime(status.nextFetchAt) : "-"
          }
        });
      })
      .catch(() => this.setData({ fetchStatus: null }));
  },

  loadQuota() {
    return api
      .getSubscribeQuota()
      .then((quota) => this.setData({ quota }))
      .catch(() => this.setData({ quota: null }));
  },

  loadChannels() {
    return api
      .listChannels()
      .then((channels) => {
        this.setData({
          channels: (channels || []).map((ch) => ({
            ...ch,
            name: format.providerName(ch.provider),
            testing: false
          }))
        });
      })
      .catch(() => this.setData({ channels: [] }));
  },

  // 开启提醒：wx.requestSubscribeMessage 必须由用户点击直接触发，
  // 模板 ID 已在 loadQuota 时缓存到 data，这里同步调用。
  requestMessage() {
    const quota = this.data.quota;
    const templateId = quota && quota.templateId;
    if (!templateId) {
      wx.showModal({
        title: "无法开启提醒",
        content: "服务端未配置订阅消息模板 ID（WX_SUBSCRIBE_TEMPLATE_ID），请先在小程序后台申请模板并配置。",
        showCancel: false
      });
      return;
    }
    if (this.data.granting) return;

    wx.requestSubscribeMessage({
      tmplIds: [templateId],
      success: (result) => {
        if (result[templateId] === "accept") {
          this.setData({ granting: true });
          api
            .grantSubscribeMessage([templateId])
            .then(() => {
              wx.showToast({ title: "提醒已开启 +1" });
              return this.refresh();
            })
            .catch((err) => wx.showToast({ title: err.message || "上报失败", icon: "none" }))
            .finally(() => this.setData({ granting: false }));
        } else if (result[templateId] === "reject") {
          wx.showToast({ title: "已拒绝授权", icon: "none" });
        } else {
          wx.showToast({ title: "授权未完成", icon: "none" });
        }
      },
      fail: (err) => {
        wx.showModal({
          title: "授权失败",
          content: (err.errMsg || "未知错误") + "。请确认模板 ID 属于当前小程序。",
          showCancel: false
        });
      }
    });
  },

  testChannel(event) {
    const { id, index } = event.currentTarget.dataset;
    this.setData({ [`channels[${index}].testing`]: true });
    api
      .testChannel(id)
      .then(() => wx.showToast({ title: "测试消息已发送" }))
      .catch((err) => {
        wx.showModal({ title: "测试失败", content: err.message || "未知错误", showCancel: false });
      })
      .finally(() => {
        this.setData({ [`channels[${index}].testing`]: false });
        this.loadQuota();
      });
  },

  toggleChannel(event) {
    const { id } = event.currentTarget.dataset;
    const enabled = event.detail.value;
    api
      .setChannelEnabled(id, enabled)
      .then(() => wx.showToast({ title: enabled ? "已启用" : "已停用", icon: "none" }))
      .catch((err) => {
        wx.showToast({ title: err.message || "操作失败", icon: "none" });
        this.loadChannels();
      });
  },

  retryLogin() {
    app
      .ensureLogin(true)
      .then(() => this.refresh())
      .catch((err) => wx.showToast({ title: err.message || "登录失败", icon: "none" }));
  },

  logout() {
    wx.showModal({
      title: "退出登录",
      content: "退出后订阅数据仍保留在服务端，重新登录即可恢复。",
      success: (res) => {
        if (!res.confirm) return;
        app.logout();
        this.setData({ loggedIn: false, user: null, quota: null, channels: [] });
        wx.showToast({ title: "已退出", icon: "none" });
      }
    });
  }
});
