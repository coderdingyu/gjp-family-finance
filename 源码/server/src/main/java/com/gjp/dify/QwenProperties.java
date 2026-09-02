package com.gjp.dify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通义千问直连。表格文本走这条会比 Dify 工作流快一截。
 * Key 只从环境变量读，不入库、不回传前端。
 */
@ConfigurationProperties(prefix = "gjp.qwen")
public class QwenProperties {

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey = "";
    private String textModel = "qwen-plus";
    private String visionModel = "qwen-vl-plus";
    private int timeoutSeconds = 60;

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getTextModel() {
        return textModel;
    }

    public void setTextModel(String textModel) {
        this.textModel = textModel;
    }

    public String getVisionModel() {
        return visionModel;
    }

    public void setVisionModel(String visionModel) {
        this.visionModel = visionModel;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
