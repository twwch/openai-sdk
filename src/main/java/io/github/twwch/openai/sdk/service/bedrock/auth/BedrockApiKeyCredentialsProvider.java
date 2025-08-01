package io.github.twwch.openai.sdk.service.bedrock.auth;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;

/**
 * Bedrock API Key专用凭证提供者
 * 完全绕过AWS SDK的默认凭证链，强制使用提供的API Key
 */
public class BedrockApiKeyCredentialsProvider implements AwsCredentialsProvider {
    
    private final String apiKey;
    private final String apiSecret;
    private final String sessionToken;
    private final boolean isApiKeyFormat;
    
    /**
     * 创建基本凭证提供者（两个参数）
     */
    public BedrockApiKeyCredentialsProvider(String apiKey, String apiSecret) {
        this(apiKey, apiSecret, null);
    }
    
    /**
     * 创建会话凭证提供者（三个参数）
     */
    public BedrockApiKeyCredentialsProvider(String apiKey, String apiSecret, String sessionToken) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key不能为空");
        }
        if (apiSecret == null || apiSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("API Secret不能为空");
        }
        
        this.apiKey = apiKey.trim();
        this.apiSecret = apiSecret.trim();
        this.sessionToken = sessionToken != null ? sessionToken.trim() : null;
        
        // 检测是否为Bedrock API Key格式
        this.isApiKeyFormat = apiKey.startsWith("BedrockAPIKey-");
        
        if (isApiKeyFormat) {
            System.out.println("检测到Bedrock API Key格式凭证");
        }
    }
    
    @Override
    public AwsCredentials resolveCredentials() {
        // 强制返回提供的凭证，不进行任何链式查找
        if (sessionToken != null && !sessionToken.isEmpty()) {
            return AwsSessionCredentials.create(apiKey, apiSecret, sessionToken);
        } else {
            return AwsBasicCredentials.create(apiKey, apiSecret);
        }
    }
    
    /**
     * 判断是否为Bedrock API Key格式
     */
    public boolean isApiKeyFormat() {
        return isApiKeyFormat;
    }
    
    /**
     * 获取API Key（用于调试，只返回前缀）
     */
    public String getApiKeyPrefix() {
        if (apiKey.length() > 10) {
            return apiKey.substring(0, 10) + "...";
        }
        return "***";
    }
}