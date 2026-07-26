const config = require("./config");

App({
  globalData: {
    apiBaseUrl: config.apiBaseUrl,
    token: "",
    user: null
  },

  _loginPromise: null,

  onLaunch() {
    const token = wx.getStorageSync("token");
    if (token) {
      this.globalData.token = token;
    }
    const user = wx.getStorageSync("user");
    if (user) {
      this.globalData.user = user;
    }
    // 启动时静默登录/校验登录态
    this.ensureLogin().catch(() => {});
  },

  setToken(token) {
    this.globalData.token = token || "";
    if (token) {
      wx.setStorageSync("token", token);
    } else {
      wx.removeStorageSync("token");
    }
  },

  setUser(user) {
    this.globalData.user = user || null;
    if (user) {
      wx.setStorageSync("user", user);
    } else {
      wx.removeStorageSync("user");
    }
  },

  /**
   * 确保已登录主服务，返回 Promise<token>。
   * force=true 时强制重新走 wx.login（用于 401 后重试）。
   * 并发调用共享同一个登录 Promise，避免重复请求 code。
   */
  ensureLogin(force) {
    if (!force && this.globalData.token) {
      return Promise.resolve(this.globalData.token);
    }
    if (this._loginPromise) {
      return this._loginPromise;
    }
    const api = require("./utils/api");
    this._loginPromise = api
      .wxLogin()
      .then((code) => api.loginWithWechat(code))
      .then((res) => {
        this.setToken(res.token);
        this.setUser({
          userId: res.userId,
          username: res.username,
          nickname: res.nickname,
          avatar: res.avatar
        });
        return res.token;
      })
      .finally(() => {
        this._loginPromise = null;
      });
    return this._loginPromise;
  },

  logout() {
    this.setToken("");
    this.setUser(null);
  }
});
