package com.openai.sdk;

/**
 * OpenAI API配置类
 */
public class OpenAIConfig {
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private String apiKey;
    private String baseUrl;
    private int timeout;
    private String organization;

    /**
     * 创建默认配置
     * @param apiKey OpenAI API密钥
     */
    public OpenAIConfig(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    /**
     * 创建自定义基础URL的配置
     * @param apiKey OpenAI API密钥
     * @param baseUrl API基础URL
     */
    public OpenAIConfig(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, 30, null);
    }

    /**
     * 创建完整自定义配置
     * @param apiKey OpenAI API密钥
     * @param baseUrl API基础URL
     * @param timeout 超时时间（秒）
     * @param organization 组织ID（可选）
     */
    public OpenAIConfig(String apiKey, String baseUrl, int timeout, String organization) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API密钥不能为空");
        }
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("基础URL不能为空");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = timeout;
        this.organization = organization;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getTimeout() {
        return timeout;
    }

    public String getOrganization() {
        return organization;
    }

    public void setApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API密钥不能为空");
        }
        this.apiKey = apiKey;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("基础URL不能为空");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }
    
    /**
     * 是否为Azure OpenAI配置
     * @return 是否为Azure OpenAI配置
     */
    public boolean isAzure() {
        return false;
    }
}
