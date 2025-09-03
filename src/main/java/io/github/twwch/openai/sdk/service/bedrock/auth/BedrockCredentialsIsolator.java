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
                        .maxConnections(200)
                        .connectionTimeout(Duration.ofSeconds(30))
                        .connectionAcquisitionTimeout(Duration.ofSeconds(30))
                        .socketTimeout(Duration.ofSeconds(60))
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
                        .apiCallAttemptTimeout(Duration.ofMinutes(5)) // 单次尝试超时：5分钟（支持长内容生成）
                        .apiCallTimeout(Duration.ofMinutes(5)))       // 总超时：5分钟（支持超长流式响应）
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        // 连接池配置：平衡并发性能和资源利用
                        .maxConcurrency(100)                            // 最大并发连接数
                        .connectionTimeout(Duration.ofSeconds(30))     // 建立连接超时：30秒
                        .connectionAcquisitionTimeout(Duration.ofSeconds(90))  // 获取连接超时：90秒
                        .maxPendingConnectionAcquires(500)             // 等待队列大小
                        
                        // 流式响应超时配置（关键）
                        .readTimeout(Duration.ofMinutes(2))            // 读取超时：2分钟（数据块之间的最大间隔）
                        .writeTimeout(Duration.ofSeconds(30))          // 写入超时：30秒
                        
                        // 连接复用和清理策略
                        .connectionTimeToLive(Duration.ofMinutes(30))  // 连接最长生存时间：30分钟（支持长流）
                        .connectionMaxIdleTime(Duration.ofMinutes(2))  // 空闲连接保持：2分钟（复用频繁请求）
                        .useIdleConnectionReaper(true))                // 启用空闲连接清理
                .credentialsProvider(credentialsProvider);

        return builder.build();
    }


}