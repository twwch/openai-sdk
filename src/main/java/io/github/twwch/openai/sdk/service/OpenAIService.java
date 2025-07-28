package io.github.twwch.openai.sdk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.twwch.openai.sdk.AzureOpenAIConfig;
import io.github.twwch.openai.sdk.OpenAIConfig;
import io.github.twwch.openai.sdk.exception.OpenAIException;
import io.github.twwch.openai.sdk.http.OpenAIHttpClient;
import io.github.twwch.openai.sdk.model.ModelInfo;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI服务类
 */
public class OpenAIService {
    private final OpenAIHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAIConfig config;

    public OpenAIService(OpenAIConfig config) {
        this.config = config;
        this.httpClient = new OpenAIHttpClient(config);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取可用模型列表
     *
     * @return 模型列表
     * @throws OpenAIException 如果请求失败
     */
    public List<ModelInfo> listModels() throws OpenAIException {
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
        String response = httpClient.get("/models/" + modelId);
        try {
            return objectMapper.readValue(response, ModelInfo.class);
        } catch (JsonProcessingException e) {
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
        // 如果是Azure OpenAI，并且没有设置模型，则使用部署ID作为模型
        if (config.isAzure() && (request.getModel() == null || request.getModel().isEmpty())) {
            AzureOpenAIConfig azureConfig = (AzureOpenAIConfig) config;
            request.setModel(azureConfig.getDeploymentId());
        }
        
        String response = httpClient.post("/chat/completions", request);
        try {
            return objectMapper.readValue(response, ChatCompletionResponse.class);
        } catch (JsonProcessingException e) {
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
        // 设置流式标志
        request.setStream(true);
        
        // 如果是Azure OpenAI，并且没有设置模型，则使用部署ID作为模型
        if (config.isAzure() && (request.getModel() == null || request.getModel().isEmpty())) {
            AzureOpenAIConfig azureConfig = (AzureOpenAIConfig) config;
            request.setModel(azureConfig.getDeploymentId());
        }
        
        EventSource eventSource = httpClient.postStream("/chat/completions", request, new EventSourceListener() {
            private volatile boolean isDone = false;
            
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
                    if (onChunk != null) {
                        onChunk.accept(chunk);
                    }
                } catch (JsonProcessingException e) {
                    isDone = true;
                    eventSource.cancel(); // 出错时关闭连接
                    if (onError != null) {
                        onError.accept(new OpenAIException("无法解析流式响应: " + data, e));
                    }
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                // 如果是因为我们主动取消导致的失败，忽略错误
                if (!isDone && !(t instanceof java.net.SocketException && t.getMessage() != null && 
                    (t.getMessage().contains("Socket closed") || t.getMessage().contains("stream was reset: CANCEL")))) {
                    if (onError != null) {
                        onError.accept(new OpenAIException("流式请求失败", t));
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