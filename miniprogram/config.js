/**
 * 主服务地址配置。
 * 生产环境必须是 HTTPS 固定域名，并在小程序后台「开发管理-开发设置-服务器域名」
 * 将其加入 request 合法域名。
 * 开发者工具联调时勾选「不校验合法域名」即可使用本地地址。
 */
const PROD_API_BASE_URL = "https://your-api-domain.com";
const DEV_API_BASE_URL = "http://127.0.0.1:8080";

function resolveApiBaseUrl() {
  try {
    const env = wx.getAccountInfoSync().miniProgram.envVersion;
    // develop: 开发者工具/真机调试; trial: 体验版; release: 正式版
    if (env === "develop") {
      return DEV_API_BASE_URL;
    }
  } catch (e) {
    // 取不到环境信息时按生产处理
  }
  return PROD_API_BASE_URL;
}

/**
 * 工具广场的第三方小程序跳转位（wx.navigateToMiniProgram）。
 * 填入目标小程序 appId 后自动出现在首页工具广场，例如天气/汇率类小程序：
 *   { name: "天气", icon: "天", appId: "wx1234567890abcdef", path: "" }
 */
const THIRD_PARTY_TOOLS = [];

module.exports = {
  apiBaseUrl: resolveApiBaseUrl(),
  thirdPartyTools: THIRD_PARTY_TOOLS
};
