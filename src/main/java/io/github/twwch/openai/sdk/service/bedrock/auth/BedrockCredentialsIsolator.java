package io.github.twwch.openai.sdk.service.bedrock.auth;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

import java.time.Duration;

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


        // 构建隔离的异步客户端
        // 流式请求需要更长的超时时间，因为响应可能持续较长时间
        BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder()
                .region(Region.of(region))
//                .overrideConfiguration(o -> o
//                        .apiCallAttemptTimeout(Duration.ofMinutes(5))  // 单次尝试超时：5分钟
//                        .apiCallTimeout(Duration.ofMinutes(10)))       // 总超时：10分钟（适合长流式响应）
//                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
//                        .maxConcurrency(200)
//                        .connectionTimeout(Duration.ofSeconds(30))     // 连接超时保持30秒
//                        .connectionAcquisitionTimeout(Duration.ofSeconds(30))
//                        .readTimeout(Duration.ofMinutes(5))            // 读取超时：5分钟（流式需要更长）
//                )
                .credentialsProvider(credentialsProvider);

        return builder.build();
    }


}