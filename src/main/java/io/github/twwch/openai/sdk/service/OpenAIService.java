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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI服务类
 */
public class OpenAIService {
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
        // 如果是Bedrock，使用Bedrock服务
        if (config.isBedrock()) {
            return bedrockService.createChatCompletion(request);
        }
        // 如果是Azure OpenAI，并且没有设置模型，则使用部署ID作为模型
        if (config.isAzure() && (request.getModel() == null || request.getModel().isEmpty())) {
            AzureOpenAIConfig azureConfig = (AzureOpenAIConfig) config;
            request.setModel(azureConfig.getDeploymentId());
        }
        
        String response = httpClient.post("/chat/completions", request);
        try {
            return objectMapper.readValue(response, ChatCompletionResponse.class);
        } catch (JsonProcessingException e) {
            logger.error("解析聊天完成响应失败 - 模型: {}, 响应: {}", request.getModel(), response, e);
            throw new OpenAIException("无法解析聊天完成响应", e);
        }
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
        // 如果是Bedrock，使用Bedrock服务
        if (config.isBedrock()) {
            bedrockService.createChatCompletionStream(request, onChunk, onComplete, onError);
            return;
        }
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
}