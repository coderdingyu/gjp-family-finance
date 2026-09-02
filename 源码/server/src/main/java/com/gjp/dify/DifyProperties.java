package com.gjp.dify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地 Dify 对接配置。API Key 只从环境变量 / 本机 yml 读取，不入库、不回传前端。
 */
@ConfigurationProperties(prefix = "gjp.dify")
public class DifyProperties {

    private String baseUrl = "http://127.0.0.1/v1";
    private String apiKey = "";
    /** chat 或 workflow */
    private String mode = "chat";
    private String user = "gjp";
    private String fileVar = "bill_file";
    private String textVar = "text_content";
    private String categoriesVar = "categories";
    private int timeoutSeconds = 180;

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean workflowMode() {
        return "workflow".equalsIgnoreCase(mode);
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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getFileVar() {
        return fileVar;
    }

    public void setFileVar(String fileVar) {
        this.fileVar = fileVar;
    }

    public String getTextVar() {
        return textVar;
    }

    public void setTextVar(String textVar) {
        this.textVar = textVar;
    }

    public String getCategoriesVar() {
        return categoriesVar;
    }

    public void setCategoriesVar(String categoriesVar) {
        this.categoriesVar = categoriesVar;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
