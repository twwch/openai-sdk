package io.github.twwch.openai.sdk.service.bedrock.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
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
     * 创建隔离的同步客户端（使用默认凭证链）
     */
    public static BedrockRuntimeClient createIsolatedClientWithDefaultCredentials(String region) {
        logger.info("创建使用默认凭证链的Bedrock同步客户端 - 区域: {}", region);

        // 使用默认凭证链（支持 ~/.aws/credentials, 环境变量, IAM角色等）
        AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();

        // 构建客户端
        BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(o -> o.apiCallAttemptTimeout(Duration.ofSeconds(60)))
                .overrideConfiguration(o -> o.apiCallTimeout(Duration.ofSeconds(120)))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(50)                            // 降低最大连接数，避免连接池耗尽
                        .connectionTimeout(Duration.ofSeconds(10))     // 建立连接超时：10秒
                        .connectionAcquisitionTimeout(Duration.ofSeconds(30))  // 降低获取连接超时：30秒
                        .socketTimeout(Duration.ofSeconds(55))         // Socket超时：55秒
                        .connectionTimeToLive(Duration.ofMinutes(5))   // 连接生存时间：5分钟
                        .connectionMaxIdleTime(Duration.ofSeconds(30)) // 空闲连接保持：30秒
                )
                .credentialsProvider(credentialsProvider);

        return builder.build();
    }

    /**
     * 创建隔离的同步客户端（使用显式凭证）
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
                .overrideConfiguration(o -> o.apiCallAttemptTimeout(Duration.ofSeconds(60)))
                .overrideConfiguration(o -> o.apiCallTimeout(Duration.ofSeconds(120)))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(50)                            // 降低最大连接数，避免连接池耗尽
                        .connectionTimeout(Duration.ofSeconds(10))     // 建立连接超时：10秒
                        .connectionAcquisitionTimeout(Duration.ofSeconds(30))  // 降低获取连接超时：30秒
                        .socketTimeout(Duration.ofSeconds(55))         // Socket超时：55秒
                        .connectionTimeToLive(Duration.ofMinutes(5))   // 连接生存时间：5分钟
                        .connectionMaxIdleTime(Duration.ofSeconds(30)) // 空闲连接保持：30秒
                )
                .credentialsProvider(credentialsProvider);

        return builder.build();
    }

    /**
     * 创建隔离的异步客户端（使用默认凭证链）
     */
    public static BedrockRuntimeAsyncClient createIsolatedAsyncClientWithDefaultCredentials(String region) {
        logger.info("创建使用默认凭证链的Bedrock异步客户端 - 区域: {}", region);

        // 使用默认凭证链（支持 ~/.aws/credentials, 环境变量, IAM角色等）
        AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();

        // 构建隔离的异步客户端
        // 优化连接池配置以支持高并发流式请求
        // 允许通过系统属性覆盖异步HTTP客户端配置
        int maxConcurrency = Integer.getInteger("bedrock.http.maxConcurrency", 20);
        int connectionTimeoutSeconds = Integer.getInteger("bedrock.http.connectionTimeoutSeconds", 30);
        int acquireTimeoutSeconds = Integer.getInteger("bedrock.http.acquireTimeoutSeconds", 60);
        int maxPending = Integer.getInteger("bedrock.http.maxPendingAcquires", 50);
        int readTimeoutMinutes = Integer.getInteger("bedrock.http.readTimeoutMinutes", 15);
        int writeTimeoutSeconds = Integer.getInteger("bedrock.http.writeTimeoutSeconds", 30);
        int ttlMinutes = Integer.getInteger("bedrock.http.ttlMinutes", 3);
        int maxIdleSeconds = Integer.getInteger("bedrock.http.maxIdleSeconds", 20);

        BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(o -> o
                        .apiCallAttemptTimeout(Duration.ofMinutes(1)) // 单次尝试超时：2分钟
                        .apiCallTimeout(Duration.ofMinutes(2)))       // 总超时：2分钟
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        // 连接池配置（可通过系统属性覆盖）
                        .maxConcurrency(maxConcurrency)                            // bedrock.http.maxConcurrency（默认20）
                        .connectionTimeout(Duration.ofSeconds(connectionTimeoutSeconds))     // bedrock.http.connectionTimeoutSeconds（默认15）
                        .connectionAcquisitionTimeout(Duration.ofSeconds(acquireTimeoutSeconds))  // bedrock.http.acquireTimeoutSeconds（默认60）
                        .maxPendingConnectionAcquires(maxPending)              // bedrock.http.maxPendingAcquires（默认50）

                        // 流式响应超时配置
                        .readTimeout(Duration.ofMinutes(readTimeoutMinutes))           // bedrock.http.readTimeoutMinutes（默认15）
                        .writeTimeout(Duration.ofSeconds(writeTimeoutSeconds))          // bedrock.http.writeTimeoutSeconds（默认30）

                        // 连接复用和清理策略
                        .connectionTimeToLive(Duration.ofMinutes(ttlMinutes))   // bedrock.http.ttlMinutes（默认3）
                        .connectionMaxIdleTime(Duration.ofSeconds(maxIdleSeconds)) // bedrock.http.maxIdleSeconds（默认20）
                        .useIdleConnectionReaper(true))                // 启用空闲连接清理
                .credentialsProvider(credentialsProvider);

        return builder.build();
    }

    /**
     * 创建隔离的异步客户端（使用显式凭证）
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
        // 允许通过系统属性覆盖异步HTTP客户端配置
        int maxConcurrency = Integer.getInteger("bedrock.http.maxConcurrency", 20);
        int connectionTimeoutSeconds = Integer.getInteger("bedrock.http.connectionTimeoutSeconds", 15);
        int acquireTimeoutSeconds = Integer.getInteger("bedrock.http.acquireTimeoutSeconds", 60);
        int maxPending = Integer.getInteger("bedrock.http.maxPendingAcquires", 50);
        int readTimeoutMinutes = Integer.getInteger("bedrock.http.readTimeoutMinutes", 15);
        int writeTimeoutSeconds = Integer.getInteger("bedrock.http.writeTimeoutSeconds", 30);
        int ttlMinutes = Integer.getInteger("bedrock.http.ttlMinutes", 3);
        int maxIdleSeconds = Integer.getInteger("bedrock.http.maxIdleSeconds", 20);

        BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(o -> o
                        .apiCallAttemptTimeout(Duration.ofMinutes(2)) // 单次尝试超时：2分钟
                        .apiCallTimeout(Duration.ofMinutes(2)))       // 总超时：2分钟
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        // 连接池配置：进一步降低并发数，确保稳定性

                        .maxConcurrency(20)                            // 进一步降低最大并发连接数
                        .connectionTimeout(Duration.ofSeconds(15))     // 增加建立连接超时：15秒
                        .connectionAcquisitionTimeout(Duration.ofSeconds(60))  // 增加获取连接超时：60秒
                        .maxPendingConnectionAcquires(50)              // 进一步降低等待队列大小

                        // 流式响应超时配置
                        .readTimeout(Duration.ofMinutes(15))           // 增加读取超时：15min（支持更长时间流式响应）
                        .writeTimeout(Duration.ofSeconds(30))          // 增加写入超时：30秒

                        // 连接复用和清理策略（更保守的配置）
                        .connectionTimeToLive(Duration.ofMinutes(3))   // 进一步降低连接生存时间：3分钟
                        .connectionMaxIdleTime(Duration.ofSeconds(20)) // 降低空闲连接保持：20秒
                        .useIdleConnectionReaper(true))                // 启用空闲连接清理
                .credentialsProvider(credentialsProvider);

        return builder.build();
    }


}