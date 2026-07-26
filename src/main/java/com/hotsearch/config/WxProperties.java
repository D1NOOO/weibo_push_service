package com.hotsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序相关配置（app.wx.*）。
 * appid/secret 用于 code2session；subscribe 描述订阅消息模板；cloud 描述云函数调用方式。
 */
@ConfigurationProperties(prefix = "app.wx")
public class WxProperties {

    private String appid = "";
    private String secret = "";
    private final Subscribe subscribe = new Subscribe();
    private final Cloud cloud = new Cloud();

    public String getAppid() { return appid; }
    public void setAppid(String appid) { this.appid = appid; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public Subscribe getSubscribe() { return subscribe; }
    public Cloud getCloud() { return cloud; }

    public boolean isLoginConfigured() {
        return appid != null && !appid.isBlank() && secret != null && !secret.isBlank();
    }

    public static class Subscribe {
        /** 订阅消息模板 ID，需与小程序后台申请的模板一致 */
        private String templateId = "";
        /** 点击订阅消息后打开的小程序页面 */
        private String page = "pages/matches/index";
        /** developer / trial / formal */
        private String miniprogramState = "formal";
        /**
         * 语义字段 -> 模板 data key 的映射；value 留空则不发送该字段。
         * 语义字段：keyword / rank / hotValue / time / ruleName
         */
        private Map<String, String> fieldMapping = defaultMapping();

        private static Map<String, String> defaultMapping() {
            Map<String, String> mapping = new LinkedHashMap<>();
            mapping.put("keyword", "thing1");
            mapping.put("rank", "character_string2");
            mapping.put("hotValue", "number3");
            mapping.put("time", "time4");
            return mapping;
        }

        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
        public String getPage() { return page; }
        public void setPage(String page) { this.page = page; }
        public String getMiniprogramState() { return miniprogramState; }
        public void setMiniprogramState(String miniprogramState) { this.miniprogramState = miniprogramState; }
        public Map<String, String> getFieldMapping() { return fieldMapping; }
        public void setFieldMapping(Map<String, String> fieldMapping) { this.fieldMapping = fieldMapping; }
    }

    public static class Cloud {
        /** http-trigger：云开发 HTTP 访问服务；openapi：invokecloudfunction */
        private String invokeMode = "http-trigger";
        /** http-trigger 模式下云函数的完整公网 URL */
        private String httpTriggerUrl = "";
        /** openapi 模式下的云开发环境 ID */
        private String envId = "";
        private String functionName = "sendSubscribeMessage";
        /** 与云函数环境变量 SUBSCRIBE_MESSAGE_SHARED_SECRET 一致 */
        private String sharedSecret = "";

        public String getInvokeMode() { return invokeMode; }
        public void setInvokeMode(String invokeMode) { this.invokeMode = invokeMode; }
        public String getHttpTriggerUrl() { return httpTriggerUrl; }
        public void setHttpTriggerUrl(String httpTriggerUrl) { this.httpTriggerUrl = httpTriggerUrl; }
        public String getEnvId() { return envId; }
        public void setEnvId(String envId) { this.envId = envId; }
        public String getFunctionName() { return functionName; }
        public void setFunctionName(String functionName) { this.functionName = functionName; }
        public String getSharedSecret() { return sharedSecret; }
        public void setSharedSecret(String sharedSecret) { this.sharedSecret = sharedSecret; }

        public boolean isConfigured() {
            if (sharedSecret == null || sharedSecret.isBlank()) return false;
            if ("openapi".equalsIgnoreCase(invokeMode)) {
                return envId != null && !envId.isBlank();
            }
            return httpTriggerUrl != null && !httpTriggerUrl.isBlank();
        }
    }
}
