package com.openai.sdk;

/**
 * Azure OpenAI API配置类
 */
public class AzureOpenAIConfig extends OpenAIConfig {
    private String resourceName;
    private String deploymentId;
    private String apiVersion;

    /**
     * 创建Azure OpenAI配置
     * @param apiKey Azure OpenAI API密钥
     * @param resourceName Azure资源名称
     * @param deploymentId 部署ID
     */
    public AzureOpenAIConfig(String apiKey, String resourceName, String deploymentId) {
        this(apiKey, resourceName, deploymentId, "2023-05-15");
    }

    /**
     * 创建Azure OpenAI配置（带API版本）
     * @param apiKey Azure OpenAI API密钥
     * @param resourceName Azure资源名称
     * @param deploymentId 部署ID
     * @param apiVersion API版本
     */
    public AzureOpenAIConfig(String apiKey, String resourceName, String deploymentId, String apiVersion) {
        super(apiKey, buildBaseUrl(resourceName), 30, null);
        this.resourceName = resourceName;
        this.deploymentId = deploymentId;
        this.apiVersion = apiVersion;
    }

    /**
     * 构建Azure OpenAI基础URL
     * @param resourceName Azure资源名称
     * @return 基础URL
     */
    private static String buildBaseUrl(String resourceName) {
        return "https://" + resourceName + ".openai.azure.com";
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
        setBaseUrl(buildBaseUrl(resourceName));
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    /**
     * 是否为Azure OpenAI配置
     * @return 是否为Azure OpenAI配置
     */
    public boolean isAzure() {
        return true;
    }
}