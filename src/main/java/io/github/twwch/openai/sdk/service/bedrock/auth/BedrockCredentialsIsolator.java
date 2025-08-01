package io.github.twwch.openai.sdk.service.bedrock.auth;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;

import java.util.HashMap;
import java.util.Map;

/**
 * Bedrock凭证隔离器
 * 确保使用API Key时完全隔离AWS环境凭证
 */
public class BedrockCredentialsIsolator {
    
    /**
     * 创建隔离的同步客户端
     */
    public static BedrockRuntimeClient createIsolatedClient(String region, 
                                                           String apiKey, 
                                                           String apiSecret, 
                                                           String sessionToken) {
        
        // 创建隔离的凭证提供者
        AwsCredentialsProvider credentialsProvider = new BedrockApiKeyCredentialsProvider(
            apiKey, apiSecret, sessionToken
        );
        
        // 创建客户端配置，禁用环境变量查找
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
            // 设置自定义用户代理
            .putAdvancedOption(SdkAdvancedClientOption.USER_AGENT_SUFFIX, "BedrockAPIKey")
            .build();
        
        // 构建隔离的客户端
        BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(overrideConfig);
        
        // 强制清除环境变量（仅在构建客户端时）
        Map<String, String> originalEnv = clearAwsEnvironment();
        try {
            return builder.build();
        } finally {
            // 恢复环境变量
            restoreEnvironment(originalEnv);
        }
    }
    
    /**
     * 创建隔离的异步客户端
     */
    public static BedrockRuntimeAsyncClient createIsolatedAsyncClient(String region, 
                                                                     String apiKey, 
                                                                     String apiSecret, 
                                                                     String sessionToken) {
        
        // 创建隔离的凭证提供者
        AwsCredentialsProvider credentialsProvider = new BedrockApiKeyCredentialsProvider(
            apiKey, apiSecret, sessionToken
        );
        
        // 创建客户端配置
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
            .putAdvancedOption(SdkAdvancedClientOption.USER_AGENT_SUFFIX, "BedrockAPIKey")
            .build();
        
        // 构建隔离的异步客户端
        BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider)
            .overrideConfiguration(overrideConfig);
        
        // 强制清除环境变量
        Map<String, String> originalEnv = clearAwsEnvironment();
        try {
            return builder.build();
        } finally {
            // 恢复环境变量
            restoreEnvironment(originalEnv);
        }
    }
    
    /**
     * 临时清除AWS环境变量
     * 注意：这是一个激进的方法，仅在创建客户端时使用
     */
    private static Map<String, String> clearAwsEnvironment() {
        Map<String, String> backup = new HashMap<>();
        
        // AWS凭证相关的系统属性
        String[] awsSystemProps = {
            "aws.accessKeyId",
            "aws.secretAccessKey", 
            "aws.sessionToken",
            "aws.region"
        };
        
        // 备份并清除系统属性
        for (String prop : awsSystemProps) {
            String value = System.getProperty(prop);
            if (value != null) {
                backup.put("sys." + prop, value);
                System.clearProperty(prop);
            }
        }
        
        return backup;
    }
    
    /**
     * 恢复环境变量
     */
    private static void restoreEnvironment(Map<String, String> backup) {
        backup.forEach((key, value) -> {
            if (key.startsWith("sys.")) {
                System.setProperty(key.substring(4), value);
            }
        });
    }
}