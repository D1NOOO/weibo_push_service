const api = require("../../utils/api");
const format = require("../../utils/format");
const app = getApp();

const HOUR_OPTIONS = [
  { key: 24, text: "24 小时" },
  { key: 72, text: "3 天" },
  { key: 168, text: "7 天" }
];

Page({
  data: {
    keyword: "",
    hourOptions: HOUR_OPTIONS,
    hours: 24,
    items: [],
    bestRank: null,
    latest: null,
    queried: false,
    loading: false
  },

  onLoad(options) {
    if (options && options.keyword) {
      this.setData({ keyword: decodeURIComponent(options.keyword) });
      this.query();
    }
  },

  onKeywordInput(event) {
    this.setData({ keyword: event.detail.value });
  },

  switchHours(event) {
    this.setData({ hours: Number(event.currentTarget.dataset.key) });
    if (this.data.keyword.trim()) {
      this.query();
    }
  },

  query() {
    const keyword = this.data.keyword.trim();
    if (!keyword) {
      wx.showToast({ title: "请输入关键词", icon: "none" });
      return;
    }
    if (this.data.loading) return;
    this.setData({ loading: true });
    app
      .ensureLogin()
      .then(() => api.getKeywordTrend(keyword, this.data.hours))
      .then((rows) => {
        const items = (rows || []).map((row) => ({
          ...row,
          timeText: format.shortTime(row.fetchedAt),
          hotText: format.hotValue(row.hotValue),
          labelClass: format.labelClass(row.label)
        }));
        let bestRank = null;
        items.forEach((row) => {
          if (row.rank && (bestRank === null || row.rank < bestRank)) {
            bestRank = row.rank;
          }
        });
        this.setData({
          items,
          bestRank,
          latest: items.length ? items[0] : null,
          queried: true
        });
      })
      .catch((err) => wx.showToast({ title: err.message || "查询失败", icon: "none" }))
      .finally(() => this.setData({ loading: false }));
  }
});
