package io.github.twwch.openai.sdk;

/**
 * AWS Bedrock API配置类
 */
public class BedrockConfig extends OpenAIConfig {
    private String region;
    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;
    private String modelId;

    /**
     * 创建Bedrock配置（使用默认凭证）
     * @param region AWS区域
     * @param modelId Bedrock模型ID
     */
    public BedrockConfig(String region, String modelId) {
        super("bedrock", "https://bedrock-runtime." + region + ".amazonaws.com", 600, null);  // 10分钟超时
        this.region = region;
        this.modelId = modelId;
    }

    /**
     * 创建Bedrock配置（使用访问密钥）
     * @param region AWS区域
     * @param accessKeyId AWS访问密钥ID
     * @param secretAccessKey AWS密钥
     * @param modelId Bedrock模型ID
     */
    public BedrockConfig(String region, String accessKeyId, String secretAccessKey, String modelId) {
        super("bedrock", "https://bedrock-runtime." + region + ".amazonaws.com", 600, null);  // 10分钟超时
        this.region = region;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.modelId = modelId;
    }

    /**
     * 创建Bedrock配置（使用临时凭证）
     * @param region AWS区域
     * @param accessKeyId AWS访问密钥ID
     * @param secretAccessKey AWS密钥
     * @param sessionToken 会话令牌
     * @param modelId Bedrock模型ID
     */
    public BedrockConfig(String region, String accessKeyId, String secretAccessKey, String sessionToken, String modelId) {
        super("bedrock", "https://bedrock-runtime." + region + ".amazonaws.com", 600, null);  // 10分钟超时
        this.region = region;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.sessionToken = sessionToken;
        this.modelId = modelId;
    }

    @Override
    public boolean isAzure() {
        return false;
    }

    @Override
    public boolean isBedrock() {
        return true;
    }

    public String getRegion() {
        return region;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getModelId() {
        return modelId;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
}