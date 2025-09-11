package io.github.twwch.openai.sdk.service.bedrock.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

import java.time.Duration;

/**
 * Bedrock凭证隔离器
 * 确保使用指定的AWS凭证，隔离环境凭证
 */
public class BedrockCredentialsIsolator {
    private static final Logger logger = LoggerFactory.getLogger(BedrockCredentialsIsolator.class);

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
                .overrideConfiguration(o -> o.apiCallAttemptTimeout(Duration.ofSeconds(30)))
                .overrideConfiguration(o -> o.apiCallTimeout(Duration.ofSeconds(60)))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(100)                           // 最大连接数（与异步客户端保持一致）
                        .connectionTimeout(Duration.ofSeconds(10))     // 建立连接超时：10秒
                        .connectionAcquisitionTimeout(Duration.ofSeconds(60))  // 获取连接超时：60秒
                        .socketTimeout(Duration.ofSeconds(90))         // Socket超时：90秒
                )
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


        logger.info("创建隔离的Bedrock异步客户端 - 区域: {}", region);
        
        // 构建隔离的异步客户端
        // 优化连接池配置以支持高并发流式请求
        BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(o -> o
                        .apiCallAttemptTimeout(Duration.ofMinutes(2)) // 单次尝试超时：2分钟
                        .apiCallTimeout(Duration.ofMinutes(2)))       // 总超时：2分钟
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        // 连接池配置：优化为单个共享客户端实例
                        .maxConcurrency(200)                            // 最大并发连接数（支持大量并发请求）
                        .connectionTimeout(Duration.ofSeconds(10))     // 建立连接超时：10秒
                        .connectionAcquisitionTimeout(Duration.ofSeconds(60))  // 获取连接超时：60秒
                        .maxPendingConnectionAcquires(200)             // 等待队列大小（支持更多排队请求）
                        
                        // 流式响应超时配置
                        .readTimeout(Duration.ofMinutes(6))           // 读取超时：6min（支持长时间流式响应）
                        .writeTimeout(Duration.ofSeconds(10))          // 写入超时：10秒
                        
                        // 连接复用和清理策略（优化为长期复用）
                        .connectionTimeToLive(Duration.ofMinutes(10))  // 连接最长生存时间：10分钟
                        .connectionMaxIdleTime(Duration.ofSeconds(30)) // 空闲连接保持：30秒
                        .useIdleConnectionReaper(true))                // 启用空闲连接清理
                .credentialsProvider(credentialsProvider);

        return builder.build();
    }


}