function app() {
  return getApp();
}

function rawRequest(path, options) {
  const headers = Object.assign(
    { "Content-Type": "application/json" },
    options.header || {}
  );
  const token = app().globalData.token || wx.getStorageSync("token");
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${app().globalData.apiBaseUrl}${path}`,
      method: options.method || "GET",
      data: options.data || {},
      header: headers,
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data);
          return;
        }
        const error = new Error(
          res.data && res.data.message ? res.data.message : `HTTP ${res.statusCode}`
        );
        error.statusCode = res.statusCode;
        reject(error);
      },
      fail(err) {
        reject(new Error(err.errMsg || "网络请求失败"));
      }
    });
  });
}

/** 带 401 自动重登重试的请求封装 */
function request(path, options = {}) {
  return rawRequest(path, options).catch((err) => {
    if (err.statusCode === 401 && !options._retried) {
      app().setToken("");
      return app()
        .ensureLogin(true)
        .then(() => rawRequest(path, Object.assign({}, options, { _retried: true })));
    }
    throw err;
  });
}

function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(res) {
        if (res.code) {
          resolve(res.code);
        } else {
          reject(new Error("wx.login 未返回 code"));
        }
      },
      fail(err) {
        reject(new Error(err.errMsg || "wx.login 调用失败"));
      }
    });
  });
}

module.exports = {
  request,
  wxLogin,

  // 认证
  loginWithWechat(code) {
    return rawRequest("/api/auth/wx-login", { method: "POST", data: { code } });
  },
  getMe() {
    return request("/api/auth/me");
  },

  // 热搜
  getLatestHotSearch() {
    return request("/api/hotsearch");
  },
  getFetchStatus() {
    return request("/api/hotsearch/status");
  },
  getKeywordTrend(keyword, hours) {
    return request(`/api/hotsearch/trend?keyword=${encodeURIComponent(keyword)}&hours=${hours || 24}`);
  },

  // 订阅
  listSubscriptions() {
    return request("/api/subscriptions");
  },
  getSubscription(id) {
    return request(`/api/subscriptions/${id}`);
  },
  createSubscription(data) {
    return request("/api/subscriptions", { method: "POST", data });
  },
  previewSubscription(data) {
    return request("/api/subscriptions/preview", { method: "POST", data });
  },
  updateSubscription(id, data) {
    return request(`/api/subscriptions/${id}`, { method: "PUT", data });
  },
  deleteSubscription(id) {
    return request(`/api/subscriptions/${id}`, { method: "DELETE" });
  },
  setSubscriptionEnabled(id, enabled) {
    return request(`/api/subscriptions/${id}/enabled`, { method: "PATCH", data: { enabled } });
  },

  // 命中事件
  listMatches(hours) {
    return request(`/api/match-events?hours=${hours || 72}`);
  },

  // 通道
  listChannels() {
    return request("/api/channels");
  },
  testChannel(id) {
    return request(`/api/channels/${id}/test`, { method: "POST" });
  },
  setChannelEnabled(id, enabled) {
    return request(`/api/channels/${id}/enabled`, { method: "PATCH", data: { enabled } });
  },

  // 订阅消息额度
  getSubscribeQuota() {
    return request("/api/wx/subscribe-message/quota");
  },
  grantSubscribeMessage(acceptedTemplateIds) {
    return request("/api/wx/subscribe-message/grant", {
      method: "POST",
      data: { acceptedTemplateIds }
    });
  }
};
