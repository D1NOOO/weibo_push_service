const api = require("../../utils/api");
const format = require("../../utils/format");
const app = getApp();

const LABEL_OPTIONS = ["爆", "沸", "热", "新"];

function splitText(value) {
  return String(value || "")
    .split(/[,，]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

Page({
  data: {
    id: null,
    labelOptions: LABEL_OPTIONS.map((label) => ({ label, selected: false })),
    channels: [],
    form: {
      name: "",
      keywords: "",
      excludeKeywords: "",
      minHotValue: "",
      enabled: true
    },
    submitting: false,
    previewing: false,
    preview: null
  },

  onLoad(options) {
    if (options && options.id) {
      this.setData({ id: Number(options.id) });
      wx.setNavigationBarTitle({ title: "编辑订阅" });
    }
    app
      .ensureLogin()
      .then(() => Promise.all([this.loadChannels(), this.loadExisting()]))
      .catch((err) => wx.showToast({ title: err.message || "加载失败", icon: "none" }));
  },

  loadChannels() {
    return api
      .listChannels()
      .then((channels) => {
        const items = (channels || []).map((ch) => ({
          id: ch.id,
          name: format.providerName(ch.provider),
          enabled: ch.enabled,
          selected: false
        }));
        this.setData({ channels: items });
        this.applySelectedChannels();
      })
      .catch(() => {});
  },

  loadExisting() {
    if (!this.data.id) return Promise.resolve();
    return api.getSubscription(this.data.id).then((sub) => {
      this._existingChannelIds = sub.channelIds || [];
      this.setData({
        form: {
          name: sub.name || "",
          keywords: (sub.keywords || []).join("，"),
          excludeKeywords: (sub.excludeKeywords || []).join("，"),
          minHotValue: sub.minHotValue ? String(sub.minHotValue) : "",
          enabled: sub.enabled !== false
        },
        labelOptions: LABEL_OPTIONS.map((label) => ({
          label,
          selected: (sub.labels || []).indexOf(label) >= 0
        }))
      });
      this.applySelectedChannels();
    });
  },

  applySelectedChannels() {
    if (!this._existingChannelIds || !this.data.channels.length) return;
    this.setData({
      channels: this.data.channels.map((ch) => ({
        ...ch,
        selected: this._existingChannelIds.indexOf(ch.id) >= 0
      }))
    });
  },

  onInput(event) {
    const field = event.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: event.detail.value });
  },

  onEnabledChange(event) {
    this.setData({ "form.enabled": event.detail.value });
  },

  toggleLabel(event) {
    const index = event.currentTarget.dataset.index;
    const key = `labelOptions[${index}].selected`;
    this.setData({ [key]: !this.data.labelOptions[index].selected });
  },

  toggleChannel(event) {
    const index = event.currentTarget.dataset.index;
    const key = `channels[${index}].selected`;
    this.setData({ [key]: !this.data.channels[index].selected });
  },

  buildPayload() {
    const form = this.data.form;
    return {
      name: form.name.trim(),
      keywords: splitText(form.keywords),
      excludeKeywords: splitText(form.excludeKeywords),
      labels: this.data.labelOptions.filter((o) => o.selected).map((o) => o.label),
      minHotValue: form.minHotValue ? Number(form.minHotValue) : null,
      channelIds: this.data.channels.filter((c) => c.selected).map((c) => c.id),
      enabled: form.enabled
    };
  },

  previewRule() {
    if (this.data.previewing) return;
    this.setData({ previewing: true });
    api
      .previewSubscription(this.buildPayload())
      .then((res) => {
        const matched = (res.matched || []).slice(0, 10).map((item) => ({
          ...item,
          hotText: format.hotValue(item.hotValue),
          labelClass: format.labelClass(item.label)
        }));
        this.setData({
          preview: {
            matchedCount: res.matchedCount || 0,
            fetchedAtText: res.fetchedAt ? format.relativeTime(res.fetchedAt) : "",
            matched
          }
        });
      })
      .catch((err) => wx.showToast({ title: err.message || "预览失败", icon: "none" }))
      .finally(() => this.setData({ previewing: false }));
  },

  submit() {
    const form = this.data.form;
    if (!form.name.trim()) {
      wx.showToast({ title: "请填写订阅名称", icon: "none" });
      return;
    }
    if (this.data.submitting) return;
    this.setData({ submitting: true });

    const payload = this.buildPayload();
    const action = this.data.id
      ? api.updateSubscription(this.data.id, payload)
      : api.createSubscription(payload);

    action
      .then(() => {
        wx.showToast({ title: "已保存" });
        setTimeout(() => wx.navigateBack(), 400);
      })
      .catch((err) => {
        wx.showToast({ title: err.message || "保存失败", icon: "none" });
      })
      .finally(() => this.setData({ submitting: false }));
  },

  remove() {
    if (!this.data.id) return;
    wx.showModal({
      title: "删除订阅",
      content: "删除后不再匹配该规则，历史命中记录保留。确定删除？",
      confirmColor: "#dc2626",
      success: (res) => {
        if (!res.confirm) return;
        api
          .deleteSubscription(this.data.id)
          .then(() => {
            wx.showToast({ title: "已删除" });
            setTimeout(() => wx.navigateBack(), 400);
          })
          .catch((err) => wx.showToast({ title: err.message || "删除失败", icon: "none" }));
      }
    });
  }
});
