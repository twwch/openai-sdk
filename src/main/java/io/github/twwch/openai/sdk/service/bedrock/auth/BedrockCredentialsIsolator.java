package io.github.twwch.openai.sdk.service.bedrock.auth;

import software.amazon.awssdk.auth.credentials.*;
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
 * 确保使用指定的AWS凭证，隔离环境凭证
 */
public class BedrockCredentialsIsolator {
    
    /**
     * 创建隔离的同步客户端
     */
    public static BedrockRuntimeClient createIsolatedClient(String region, 
                                                           String accessKeyId, 
                                                           String secretAccessKey, 
                                                           String sessionToken) {
        
        // 创建标准AWS凭证提供者
        AwsCredentialsProvider credentialsProvider;
        if (sessionToken != null && !sessionToken.isEmpty()) {
            credentialsProvider = StaticCredentialsProvider.create(
                AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken)
            );
        } else {
            credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            );
        }

        // 构建隔离的客户端
        BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider);

        return builder.build();
    }
    
    /**
     * 创建隔离的异步客户端
     */
    public static BedrockRuntimeAsyncClient createIsolatedAsyncClient(String region, 
                                                                     String accessKeyId, 
                                                                     String secretAccessKey, 
                                                                     String sessionToken) {
        
        // 创建标准AWS凭证提供者
        AwsCredentialsProvider credentialsProvider;
        if (sessionToken != null && !sessionToken.isEmpty()) {
            credentialsProvider = StaticCredentialsProvider.create(
                AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken)
            );
        } else {
            credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            );
        }
        

        // 构建隔离的异步客户端
        BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider);

        return builder.build();
    }
    


}