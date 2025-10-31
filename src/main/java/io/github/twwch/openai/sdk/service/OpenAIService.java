package io.github.twwch.openai.sdk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.twwch.openai.sdk.AzureOpenAIConfig;
import io.github.twwch.openai.sdk.BedrockConfig;
import io.github.twwch.openai.sdk.OpenAIConfig;
import io.github.twwch.openai.sdk.exception.OpenAIException;
import io.github.twwch.openai.sdk.http.OpenAIHttpClient;
import io.github.twwch.openai.sdk.model.ModelInfo;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * OpenAI服务类
 */
public class OpenAIService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);
    
    private final OpenAIHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAIConfig config;
    private final BedrockService bedrockService;

    public OpenAIService(OpenAIConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        
        // 如果是Bedrock配置，创建Bedrock服务
        if (config.isBedrock()) {
            this.bedrockService = new BedrockService((BedrockConfig) config);
            this.httpClient = null;
        } else {
            this.bedrockService = null;
            this.httpClient = new OpenAIHttpClient(config);
        }
    }

    /**
     * 获取可用模型列表
     *
     * @return 模型列表
     * @throws OpenAIException 如果请求失败
     */
    public List<ModelInfo> listModels() throws OpenAIException {
        // 如果是Bedrock，使用Bedrock服务
        if (config.isBedrock()) {
            return bedrockService.listModels();
        }
        String response = httpClient.get("/models");
        try {
            if (config.isAzure()) {
                // Azure OpenAI的响应格式不同
                JsonNode root = objectMapper.readTree(response);
                List<ModelInfo> models = new ArrayList<>();
                
                if (root.has("data")) {
                    JsonNode data = root.get("data");
                    for (JsonNode model : data) {
                        models.add(objectMapper.treeToValue(model, ModelInfo.class));
                    }
                } else {
                    // 某些Azure API版本可能直接返回模型数组
                    for (JsonNode model : root) {
                        models.add(objectMapper.treeToValue(model, ModelInfo.class));
                    }
                }
                
                return models;
            } else {
                // 标准OpenAI API
                Map<String, List<ModelInfo>> result = objectMapper.readValue(response, new TypeReference<Map<String, List<ModelInfo>>>() {});
                return result.get("data");
            }
        } catch (JsonProcessingException e) {
            logger.error("解析模型列表响应失败: {}", response, e);
            throw new OpenAIException("无法解析模型列表响应", e);
        }
    }

    /**
     * 获取模型详情
     *
     * @param modelId 模型ID
     * @return 模型详情
     * @throws OpenAIException 如果请求失败
     */
    public ModelInfo getModel(String modelId) throws OpenAIException {
        // 如果是Bedrock，使用Bedrock服务
        if (config.isBedrock()) {
            return bedrockService.getModel(modelId);
        }
        String response = httpClient.get("/models/" + modelId);
        try {
            return objectMapper.readValue(response, ModelInfo.class);
        } catch (JsonProcessingException e) {
            logger.error("解析模型详情响应失败 - 模型ID: {}, 响应: {}", modelId, response, e);
            throw new OpenAIException("无法解析模型详情响应", e);
        }
    }

    /**
     * 创建聊天完成
     *
     * @param request 聊天完成请求
     * @return 聊天完成响应
     * @throws OpenAIException 如果请求失败
     */
    public ChatCompletionResponse createChatCompletion(ChatCompletionRequest request) throws OpenAIException {
        // 重试配置
        int maxRetries = 3;
        int retryCount = 0;
        long retryDelay = 1000; // 初始重试延迟1秒
        
        while (retryCount < maxRetries) {
            try {
                // 如果是Bedrock，使用Bedrock服务
                if (config.isBedrock()) {
                    return bedrockService.createChatCompletion(request);
                }
                
                // 如果是Azure OpenAI，并且没有设置模型，则使用部署ID作为模型
                if (config.isAzure() && (request.getModel() == null || request.getModel().isEmpty())) {
                    AzureOpenAIConfig azureConfig = (AzureOpenAIConfig) config;
                    request.setModel(azureConfig.getDeploymentId());
                }

                // 清除Bedrock专用字段，避免Azure/OpenAI不认识这些字段而返回400错误
                request.setBedrockEnableSystemCache(null);

                String response = httpClient.post("/chat/completions", request);
                try {
                    return objectMapper.readValue(response, ChatCompletionResponse.class);
                } catch (JsonProcessingException e) {
                    logger.error("解析聊天完成响应失败 - 模型: {}, 响应: {}", request.getModel(), response, e);
                    throw new OpenAIException("无法解析聊天完成响应", e);
                }
                
            } catch (Exception e) {
                // 判断是否是可重试的错误
                if (isRetryableError(e)) {
                    retryCount++;
                    
                    if (retryCount >= maxRetries) {
                        logger.error("达到最大重试次数 ({} 次)，放弃重试", maxRetries);
                        throw new OpenAIException("请求失败，已重试 " + maxRetries + " 次: " + e.getMessage(), e);
                    }
                    
                    logger.warn("遇到可重试错误: {}，将在 {} 毫秒后进行第 {} 次重试", 
                               e.getMessage(), retryDelay, retryCount + 1);
                    
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new OpenAIException("重试被中断", ie);
                    }
                    
                    // 指数退避，下次重试延迟翻倍
                    retryDelay = Math.min(retryDelay * 2, 10000); // 最多延迟10秒
                    
                } else {
                    // 不可重试的错误，直接抛出
                    throw e;
                }
            }
        }
        
        // 不应该到达这里
        throw new OpenAIException("意外的重试逻辑错误");
    }
    
    /**
     * 判断是否是可重试的错误
     */
    private boolean isRetryableError(Exception e) {
        if (e == null) {
            return false;
        }
        
        String message = e.getMessage();
        if (message == null) {
            message = "";
        }
        
        // 超时错误
        if (e instanceof SocketTimeoutException || 
            message.contains("timeout") || 
            message.contains("Timeout") ||
            message.contains("timed out")) {
            logger.debug("检测到超时错误: {}", message);
            return true;
        }
        
        // 网络错误
        if (e instanceof IOException || 
            message.contains("connection") || 
            message.contains("Connection") ||
            message.contains("Network") ||
            message.contains("network") ||
            message.contains("UnknownHost") ||
            message.contains("SocketException") ||
            message.contains("ConnectException")) {
            logger.debug("检测到网络错误: {}", message);
            return true;
        }
        
        // 连接池错误
        if (message.contains("connection pool") || 
            message.contains("Connection pool") ||
            message.contains("Acquire operation") ||
            message.contains("ClosedChannelException") ||
            message.contains("pool exhausted") ||
            message.contains("No connection available")) {
            logger.debug("检测到连接池错误: {}", message);
            return true;
        }
        
        // AWS SDK 特定的可重试错误
        if (message.contains("ThrottlingException") ||
            message.contains("TooManyRequestsException") ||
            message.contains("RequestTimeout") ||
            message.contains("ServiceUnavailable") ||
            message.contains("Unable to execute HTTP request")) {
            logger.debug("检测到AWS SDK可重试错误: {}", message);
            return true;
        }
        
        // HTTP 5xx 错误（服务器错误）
        if (message.contains("500") || 
            message.contains("502") || 
            message.contains("503") || 
            message.contains("504")) {
            logger.debug("检测到HTTP 5xx错误: {}", message);
            return true;
        }
        
        return false;
    }

    /**
     * 创建聊天完成（流式）
     * @param request 聊天完成请求
     * @param onChunk 处理每个数据块的回调
     * @param onComplete 完成时的回调
     * @param onError 错误时的回调
     * @throws OpenAIException 如果请求失败
     */
    public void createChatCompletionStream(ChatCompletionRequest request, 
                                           Consumer<ChatCompletionChunk> onChunk,
                                           Runnable onComplete,
                                           Consumer<Throwable> onError) throws OpenAIException {
        // 重试配置
        int maxRetries = 3;
        int retryCount = 0;
        long retryDelay = 1000; // 初始重试延迟1秒
        
        while (retryCount < maxRetries) {
            try {
                // 如果是Bedrock，使用Bedrock服务
                if (config.isBedrock()) {
                    bedrockService.createChatCompletionStream(request, onChunk, onComplete, onError);
                    return;
                }
                
                // 调用内部流式方法
                createChatCompletionStreamInternal(request, onChunk, onComplete, onError);
                return; // 成功则返回
                
            } catch (Exception e) {
                // 判断是否是可重试的错误
                if (isRetryableError(e)) {
                    retryCount++;
                    
                    if (retryCount >= maxRetries) {
                        logger.error("流式请求达到最大重试次数 ({} 次)，放弃重试", maxRetries);
                        if (onError != null) {
                            onError.accept(new OpenAIException("流式请求失败，已重试 " + maxRetries + " 次: " + e.getMessage(), e));
                        }
                        throw new OpenAIException("流式请求失败，已重试 " + maxRetries + " 次: " + e.getMessage(), e);
                    }
                    
                    logger.warn("流式请求遇到可重试错误: {}，将在 {} 毫秒后进行第 {} 次重试", 
                               e.getMessage(), retryDelay, retryCount + 1);
                    
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        if (onError != null) {
                            onError.accept(new OpenAIException("重试被中断", ie));
                        }
                        throw new OpenAIException("重试被中断", ie);
                    }
                    
                    // 指数退避，下次重试延迟翻倍
                    retryDelay = Math.min(retryDelay * 2, 10000); // 最多延迟10秒
                    
                } else {
                    // 不可重试的错误，直接传递给错误处理器并抛出
                    if (onError != null) {
                        onError.accept(e);
                    }
                    throw e;
                }
            }
        }
    }
    
    /**
     * 内部流式请求方法（不带重试）
     */
    private void createChatCompletionStreamInternal(ChatCompletionRequest request, 
                                                    Consumer<ChatCompletionChunk> onChunk,
                                                    Runnable onComplete,
                                                    Consumer<Throwable> onError) throws OpenAIException {
        // 设置流式标志
        request.setStream(true);
        
        // 注释掉自动设置stream_options，让用户自己决定是否需要
        // if (request.getStreamOptions() == null) {
        //     request.setStreamOptions(new ChatCompletionRequest.StreamOptions(true));
        // }
        
        // 如果是Azure OpenAI，并且没有设置模型，则使用部署ID作为模型
        if (config.isAzure() && (request.getModel() == null || request.getModel().isEmpty())) {
            AzureOpenAIConfig azureConfig = (AzureOpenAIConfig) config;
            request.setModel(azureConfig.getDeploymentId());
        }

        // 清除Bedrock专用字段，避免Azure/OpenAI不认识这些字段而返回400错误
        request.setBedrockEnableSystemCache(null);

        EventSource eventSource = httpClient.postStream("/chat/completions", request, new EventSourceListener() {
            private volatile boolean isDone = false;
            
            @Override
            public void onOpen(EventSource eventSource, okhttp3.Response response) {
                // 检查响应状态
                if (!response.isSuccessful()) {
                    isDone = true;
                    eventSource.cancel();
                    if (onError != null) {
                        String errorMessage = "流式请求失败 (状态码: " + response.code() + ")";
                        if (response.message() != null && !response.message().isEmpty()) {
                            errorMessage += " - " + response.message();
                        }
                        
                        // 记录错误详情
                        logger.error("OpenAI API 流式请求错误 - 状态码: {}, URL: {}", 
                            response.code(), response.request().url());
                        try {
                            String requestJson = objectMapper.writeValueAsString(request);
                            logger.debug("请求参数: {}", requestJson);
                        } catch (JsonProcessingException je) {
                            logger.debug("请求参数: [无法序列化]", je);
                        }
                        
                        // 尝试读取响应体获取更多错误信息
                        try {
                            if (response.body() != null) {
                                String body = response.body().string();
                                if (!body.isEmpty()) {
                                    logger.error("错误响应: {}", body);
                                    errorMessage += " - " + body;
                                }
                            }
                        } catch (IOException e) {
                            // 忽略读取错误
                        }
                        
                        onError.accept(new OpenAIException(errorMessage));
                    }
                }
            }
            
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if ("[DONE]".equals(data)) {
                    isDone = true;
                    eventSource.cancel(); // 关闭连接
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    return;
                }
                
                try {
                    ChatCompletionChunk chunk = objectMapper.readValue(data, ChatCompletionChunk.class);

                    // 只要有chunk就调用回调，包括空内容的chunk
                    if (onChunk != null) {
                        onChunk.accept(chunk);
                    }
                } catch (JsonProcessingException e) {
                    isDone = true;
                    eventSource.cancel(); // 出错时关闭连接
                    logger.error("解析流式响应失败: {}", data, e);
                    if (onError != null) {
                        onError.accept(new OpenAIException("无法解析流式响应: " + data, e));
                    }
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                // 如果是因为我们主动取消导致的失败，忽略错误
                if (!isDone) {
                    // 判断是否是可忽略的错误
                    boolean isIgnorableError = false;
                    if (t instanceof java.net.SocketException || t instanceof java.io.IOException) {
                        String message = t.getMessage();
                        if (message != null && (
                            message.contains("Socket closed") || 
                            message.contains("stream was reset: CANCEL") ||
                            message.contains("canceled") ||
                            message.contains("Stream closed"))) {
                            isIgnorableError = true;
                        }
                    }
                    
                    if (!isIgnorableError && onError != null) {
                        // 构建更详细的错误信息
                        String errorMessage = "流式请求失败";
                        if (response != null) {
                            errorMessage += " (状态码: " + response.code() + ")";
                            if (response.message() != null && !response.message().isEmpty()) {
                                errorMessage += " - " + response.message();
                            }
                            
                            // 记录错误详情
                            logger.error("OpenAI API 流式请求失败 - 状态码: {}, URL: {}", 
                                response.code(), response.request().url(), t);
                            try {
                                String requestJson = objectMapper.writeValueAsString(request);
                                logger.debug("请求参数: {}", requestJson);
                            } catch (JsonProcessingException je) {
                                logger.debug("请求参数: [无法序列化]", je);
                            }
                            }
                        if (t != null && t.getMessage() != null) {
                            errorMessage += ": " + t.getMessage();
                        }
                        onError.accept(new OpenAIException(errorMessage, t));
                    }
                }
                eventSource.cancel(); // 确保连接被关闭
            }

            @Override
            public void onClosed(EventSource eventSource) {
                if (!isDone && onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }
    
    /**
     * 关闭服务并释放资源
     */
    @Override
    public void close() {
        try {
            if (bedrockService != null) {
                bedrockService.close();
            }
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (Exception e) {
            logger.error("关闭 OpenAIService 时发生错误", e);
        }
    }
}