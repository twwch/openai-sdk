package io.github.twwch.openai.sdk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.twwch.openai.sdk.BedrockConfig;
import io.github.twwch.openai.sdk.exception.OpenAIException;
import io.github.twwch.openai.sdk.model.ModelInfo;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.service.bedrock.BedrockModelAdapter;
import io.github.twwch.openai.sdk.service.bedrock.BedrockModelAdapterFactory;
import io.github.twwch.openai.sdk.service.bedrock.BedrockRequestValidator;
import io.github.twwch.openai.sdk.service.bedrock.auth.BedrockCredentialsIsolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Bedrock服务实现类
 */
public class BedrockService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BedrockService.class);

    private final BedrockConfig config;
    private volatile BedrockRuntimeClient client;
    private volatile BedrockRuntimeAsyncClient asyncClient;
    private final ObjectMapper objectMapper;
    private final BedrockModelAdapter modelAdapter;
    private final Object clientLock = new Object();

    public BedrockService(BedrockConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();

        logger.debug("初始化Bedrock服务 - 区域: {}, 模型: {}", config.getRegion(), config.getModelId());

        // 验证凭证
        validateCredentials();

        // 使用隔离器创建客户端，确保完全隔离AWS环境凭证
        this.client = BedrockCredentialsIsolator.createIsolatedClient(
                config.getRegion(),
                config.getAccessKeyId(),
                config.getSecretAccessKey(),
                config.getSessionToken()
        );

        this.asyncClient = BedrockCredentialsIsolator.createIsolatedAsyncClient(
                config.getRegion(),
                config.getAccessKeyId(),
                config.getSecretAccessKey(),
                config.getSessionToken()
        );

        // 创建模型适配器
        this.modelAdapter = BedrockModelAdapterFactory.createAdapter(config.getModelId());

        logger.info("Bedrock服务初始化成功 - 使用模型: {}", config.getModelId());
    }

    /**
     * 验证凭证是否已提供
     */
    private void validateCredentials() {
        if (config.getAccessKeyId() == null || config.getSecretAccessKey() == null) {
            throw new IllegalArgumentException(
                    "Bedrock服务需要显式提供AWS凭证。" +
                            "请通过 OpenAI.bedrock(region, accessKeyId, secretAccessKey, modelId) 提供凭证。"
            );
        }

    }

    /**
     * 列出可用模型
     */
    public List<ModelInfo> listModels() throws OpenAIException {
        // Bedrock不提供列出模型的API，返回预定义的模型列表
        List<ModelInfo> models = new ArrayList<>();

        // Claude模型
        models.add(createModelInfo("anthropic.claude-3-opus-20240229", "anthropic"));
        models.add(createModelInfo("anthropic.claude-3-sonnet-20240229", "anthropic"));
        models.add(createModelInfo("anthropic.claude-3-haiku-20240307", "anthropic"));
        models.add(createModelInfo("anthropic.claude-v2:1", "anthropic"));
        models.add(createModelInfo("anthropic.claude-v2", "anthropic"));
        models.add(createModelInfo("anthropic.claude-instant-v1", "anthropic"));

        // Llama模型
        models.add(createModelInfo("meta.llama2-13b-chat-v1", "meta"));
        models.add(createModelInfo("meta.llama2-70b-chat-v1", "meta"));

        // Amazon Titan模型
        models.add(createModelInfo("amazon.titan-text-express-v1", "amazon"));
        models.add(createModelInfo("amazon.titan-text-lite-v1", "amazon"));

        return models;
    }

    private ModelInfo createModelInfo(String id, String ownedBy) {
        ModelInfo modelInfo = new ModelInfo();
        modelInfo.setId(id);
        modelInfo.setObject("model");
        modelInfo.setCreated(System.currentTimeMillis() / 1000);
        modelInfo.setOwnedBy(ownedBy);
        return modelInfo;
    }

    /**
     * 获取模型详情
     */
    public ModelInfo getModel(String modelId) throws OpenAIException {
        List<ModelInfo> models = listModels();
        return models.stream()
                .filter(m -> m.getId().equals(modelId))
                .findFirst()
                .orElseThrow(() -> new OpenAIException("模型不存在: " + modelId));
    }

    /**
     * 创建聊天完成
     */
    public ChatCompletionResponse createChatCompletion(ChatCompletionRequest request) throws OpenAIException {
        String bedrockRequest = null;
        try {
            // 验证和清理请求参数
            BedrockRequestValidator.validateAndCleanRequest(request);

            // 使用配置的模型ID覆盖请求中的模型
            String modelId = config.getModelId();

            // 转换请求格式
            bedrockRequest = modelAdapter.convertRequest(request, objectMapper);

            // 检查请求大小
            if (bedrockRequest.length() > 100000) {
                logger.warn("请求体过大: {} bytes，可能超出限制", bedrockRequest.length());
            }

            logger.debug("发送Bedrock请求 - 模型: {}, 请求大小: {} bytes", modelId, bedrockRequest.length());

            // 调用Bedrock API
            InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .body(SdkBytes.fromString(bedrockRequest, StandardCharsets.UTF_8))
                    .contentType("application/json")
                    .accept("application/json")
                    .build();

            InvokeModelResponse response = client.invokeModel(invokeRequest);
            String responseBody = response.body().asUtf8String();

            // 转换响应格式
            return modelAdapter.convertResponse(responseBody, request, objectMapper);

        } catch (Exception e) {
            logger.error("Bedrock请求失败", e);
            if (bedrockRequest != null) {
                logger.error("请求体: {}", bedrockRequest);
            }
            // 如果是AWS服务异常，尝试获取更多信息
            if (e instanceof SdkServiceException) {
                SdkServiceException sdkException = (SdkServiceException) e;
                logger.error("状态码: {}", sdkException.statusCode());
                logger.error("错误消息: {}", sdkException.getMessage());
            }
            throw new OpenAIException("Bedrock请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建聊天完成（流式）
     * @return CompletableFuture 用于等待流完成
     */
    public CompletableFuture<Void> createChatCompletionStream(ChatCompletionRequest request,
                                           Consumer<ChatCompletionChunk> onChunk,
                                           Runnable onComplete,
                                           Consumer<Throwable> onError) throws OpenAIException {
        // 添加重试机制
        int maxRetries = 5;  // 增加重试次数
        Exception lastException = null;
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return createChatCompletionStreamInternal(request, onChunk, onComplete, onError);
            } catch (Exception e) {
                lastException = e;
                String message = e.getMessage();
                
                // 判断是否是可重试的错误
                if (message != null && (
                    message.contains("Acquire operation took longer") ||
                    message.contains("connection pool") ||
                    message.contains("Unable to execute HTTP request") ||
                    message.contains("ClosedChannelException") ||
                    message.contains("An error occurred on the connection") ||
                    message.contains("All streams will be closed") ||
                    message.contains("timeout"))) {
                    
                    if (attempt < maxRetries - 1) {
                        logger.warn("流式请求失败，尝试重试 ({}/{}): {}", 
                                   attempt + 1, maxRetries, message);
                        
                        // 如果是连接池相关错误，在第3次重试时重建客户端
                        if (attempt == 2 && message != null && 
                            (message.contains("ClosedChannelException") || 
                             message.contains("An error occurred on the connection"))) {
                            logger.warn("检测到连接池问题，重建客户端...");
                            rebuildAsyncClient();
                        }
                        
                        // 等待一段时间后重试（使用更长的退避时间）
                        try {
                            long backoffTime = Math.min((long)Math.pow(2, attempt) * 1000, 10000); // 指数退避，最多10秒
                            logger.info("等待 {} ms 后重试...", backoffTime);
                            Thread.sleep(backoffTime);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new OpenAIException("重试被中断", ie);
                        }
                        continue;
                    }
                }
                
                // 不可重试的错误或已达到最大重试次数
                throw e;
            }
        }
        
        // 所有重试都失败
        throw new OpenAIException("流式请求失败，已重试 " + maxRetries + " 次", lastException);
    }
    
    /**
     * 重建异步客户端
     */
    private void rebuildAsyncClient() {
        synchronized (clientLock) {
            logger.info("重建 Bedrock 异步客户端...");
            
            // 关闭旧客户端
            if (asyncClient != null) {
                try {
                    asyncClient.close();
                } catch (Exception e) {
                    logger.warn("关闭旧客户端时出错: {}", e.getMessage());
                }
            }
            
            // 创建新客户端
            this.asyncClient = BedrockCredentialsIsolator.createIsolatedAsyncClient(
                    config.getRegion(),
                    config.getAccessKeyId(),
                    config.getSecretAccessKey(),
                    config.getSessionToken()
            );
            
            logger.info("异步客户端重建完成");
        }
    }
    
    /**
     * 内部流式请求实现（不带重试）
     */
    private CompletableFuture<Void> createChatCompletionStreamInternal(ChatCompletionRequest request,
                                           Consumer<ChatCompletionChunk> onChunk,
                                           Runnable onComplete,
                                           Consumer<Throwable> onError) throws OpenAIException {
        String bedrockRequest = null;
        CompletableFuture<Void> streamCompletion = new CompletableFuture<>();
        
        try {
            // 验证和清理请求参数
            BedrockRequestValidator.validateAndCleanRequest(request);

            // 使用配置的模型ID覆盖请求中的模型
            String modelId = config.getModelId();

            // 转换请求格式（流式）
            bedrockRequest = modelAdapter.convertStreamRequest(request, objectMapper);

            // 检查请求大小
            if (bedrockRequest.length() > 100000) {
                logger.warn("请求体过大: {} bytes，可能超出限制", bedrockRequest.length());
            }

            logger.debug("发送Bedrock请求 - 模型: {}, 请求大小: {} bytes", modelId, bedrockRequest.length());

            // 调用Bedrock流式API
            InvokeModelWithResponseStreamRequest invokeRequest = InvokeModelWithResponseStreamRequest.builder()
                    .modelId(modelId)
                    .body(SdkBytes.fromString(bedrockRequest, StandardCharsets.UTF_8))
                    .contentType("application/json")
                    .accept("application/json")
                    .build();

            // 使用原子布尔值跟踪完成状态，防止重复调用回调
            final java.util.concurrent.atomic.AtomicBoolean isCompleted = new java.util.concurrent.atomic.AtomicBoolean(false);
            final java.util.concurrent.atomic.AtomicBoolean hasError = new java.util.concurrent.atomic.AtomicBoolean(false);
            
            // 处理流式响应 - 使用Visitor模式确保资源清理
            InvokeModelWithResponseStreamResponseHandler responseHandler = InvokeModelWithResponseStreamResponseHandler.builder()
                    .subscriber(responseStream -> {
                                // 处理流式响应
                        if (responseStream instanceof PayloadPart) {
                            PayloadPart payloadPart = (PayloadPart) responseStream;
                            if (hasError.get()) {
                                return; // 如果已经出错，忽略后续数据
                            }
                            
                            String chunk = payloadPart.bytes().asUtf8String();
                            try {
                                // 转换并发送chunk
                                List<ChatCompletionChunk> chunks = modelAdapter.convertStreamChunk(chunk, objectMapper);
                                for (ChatCompletionChunk completionChunk : chunks) {
                                    if (onChunk != null && !hasError.get()) {
                                        onChunk.accept(completionChunk);
                                    }
                                }
                            } catch (Exception e) {
                                logger.error("解析流式响应失败: {}", e.getMessage());
                                if (!hasError.getAndSet(true) && onError != null) {
                                    onError.accept(new OpenAIException("解析流式响应失败: " + e.getMessage(), e));
                                }
                                streamCompletion.completeExceptionally(e);
                            }
                        } else {
                            // 处理其他事件类型
                            logger.debug("收到流事件: {}", responseStream.getClass().getSimpleName());
                        }
                    })
                    .onComplete(() -> {
                        logger.debug("流式响应处理完成");
                        if (!isCompleted.getAndSet(true) && !hasError.get()) {
                            if (onComplete != null) {
                                try {
                                    onComplete.run();
                                } catch (Exception e) {
                                    logger.error("完成回调执行失败", e);
                                }
                            }
                            streamCompletion.complete(null);
                        }
                    })
                    .onError(throwable -> {
                        logger.error("Bedrock流式请求失败: {}", throwable.getMessage());
                        if (!hasError.getAndSet(true)) {
                            if (onError != null) {
                                try {
                                    onError.accept(new OpenAIException("Bedrock流式请求失败: " + throwable.getMessage(), throwable));
                                } catch (Exception e) {
                                    logger.error("错误回调执行失败", e);
                                }
                            }
                            streamCompletion.completeExceptionally(throwable);
                        }
                    })
                    .build();

            // 执行异步调用
            CompletableFuture<Void> sdkFuture = asyncClient.invokeModelWithResponseStream(invokeRequest, responseHandler);
            
            // 确保SDK的Future完成时，我们的Future也完成（用于资源清理）
            sdkFuture.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    if (!hasError.getAndSet(true)) {
                        logger.error("SDK流式请求失败", throwable);
                        streamCompletion.completeExceptionally(throwable);
                    }
                } else if (!isCompleted.get()) {
                    // 如果流没有正常完成，强制完成
                    streamCompletion.complete(null);
                }
            });
            
            // 添加超时保护，防止连接永远不释放
            streamCompletion.orTimeout(120, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    if (throwable instanceof java.util.concurrent.TimeoutException) {
                        logger.error("流式请求超时（120秒）");
                        if (!hasError.getAndSet(true) && onError != null) {
                            onError.accept(new OpenAIException("流式请求超时", throwable));
                        }
                    }
                    return null;
                });
            
            return streamCompletion;

        } catch (Exception e) {
            logger.error("Bedrock流式请求失败", e);
            if (bedrockRequest != null) {
                logger.error("请求体: {}", bedrockRequest);
            }
            if (onError != null) {
                try {
                    onError.accept(new OpenAIException("Bedrock流式请求失败: " + e.getMessage(), e));
                } catch (Exception callbackError) {
                    logger.error("错误回调执行失败", callbackError);
                }
            }
            // 确保Future被标记为失败
            streamCompletion.completeExceptionally(e);
            return streamCompletion;
        }
    }

    /**
     * 关闭服务并释放资源
     */
    @Override
    public void close() {
        synchronized (clientLock) {
            try {
                logger.debug("关闭 BedrockService，释放资源...");
            
            // 关闭同步客户端
            if (client != null) {
                try {
                    client.close();
                    logger.debug("同步客户端已关闭");
                } catch (Exception e) {
                    logger.warn("关闭同步客户端时出现警告（可能已经关闭）: {}", e.getMessage());
                }
            }
            
            // 关闭异步客户端（可能需要更长时间）
            if (asyncClient != null) {
                try {
                    // 使用异步方式关闭，避免阻塞
                    java.util.concurrent.CompletableFuture<Void> closeFuture = 
                        java.util.concurrent.CompletableFuture.runAsync(() -> {
                            try {
                                asyncClient.close();
                            } catch (Exception e) {
                                logger.warn("关闭异步客户端时出现警告: {}", e.getMessage());
                            }
                        });
                    
                    // 等待最多5秒
                    closeFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
                    logger.debug("异步客户端已关闭");
                } catch (java.util.concurrent.TimeoutException e) {
                    logger.warn("关闭异步客户端超时（5秒），可能有未完成的请求");
                } catch (Exception e) {
                    logger.warn("关闭异步客户端时出现警告: {}", e.getMessage());
                }
            }
            
            logger.debug("BedrockService 资源释放完成");
            } catch (Exception e) {
                logger.error("关闭 BedrockService 时发生错误", e);
            }
        }
    }
}