package io.github.twwch.openai.sdk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.twwch.openai.sdk.GeminiConfig;
import io.github.twwch.openai.sdk.exception.OpenAIException;
import io.github.twwch.openai.sdk.http.OpenAIHttpClient;
import io.github.twwch.openai.sdk.model.ModelInfo;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.util.ImageUtils;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Google Gemini服务类
 * 实现OpenAI兼容接口，用于与Google Gemini API交互
 */
public class GeminiService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);
    
    private final GeminiConfig config;
    private final OpenAIHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public GeminiService(GeminiConfig config) {
        this.config = config;
        this.httpClient = new OpenAIHttpClient(config);
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 获取可用模型列表
     * @return 模型列表
     * @throws OpenAIException 如果请求失败
     */
    public List<ModelInfo> listModels() throws OpenAIException {
        String response = httpClient.get("/models");
        try {
            JsonNode root = objectMapper.readTree(response);
            List<ModelInfo> models = new ArrayList<>();
            
            if (root.has("data")) {
                JsonNode data = root.get("data");
                for (JsonNode model : data) {
                    models.add(objectMapper.treeToValue(model, ModelInfo.class));
                }
            }
            
            return models;
        } catch (JsonProcessingException e) {
            logger.error("解析Gemini模型列表响应失败: {}", response, e);
            throw new OpenAIException("无法解析Gemini模型列表响应", e);
        }
    }
    
    /**
     * 获取模型详情
     * @param modelId 模型ID
     * @return 模型详情
     * @throws OpenAIException 如果请求失败
     */
    public ModelInfo getModel(String modelId) throws OpenAIException {
        String response = httpClient.get("/models/" + modelId);
        try {
            return objectMapper.readValue(response, ModelInfo.class);
        } catch (JsonProcessingException e) {
            logger.error("解析Gemini模型详情响应失败 - 模型ID: {}, 响应: {}", modelId, response, e);
            throw new OpenAIException("无法解析Gemini模型详情响应", e);
        }
    }
    
    /**
     * 创建聊天完成
     * @param request 聊天完成请求
     * @return 聊天完成响应
     * @throws OpenAIException 如果请求失败
     */
    public ChatCompletionResponse createChatCompletion(ChatCompletionRequest request) throws OpenAIException {
        // 转换请求以处理图片
        ChatCompletionRequest processedRequest = processImagesInRequest(request);
        
        String response = httpClient.post("/chat/completions", processedRequest);
        try {
            return objectMapper.readValue(response, ChatCompletionResponse.class);
        } catch (JsonProcessingException e) {
            logger.error("解析Gemini聊天完成响应失败 - 模型: {}, 响应: {}", request.getModel(), response, e);
            throw new OpenAIException("无法解析Gemini聊天完成响应", e);
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
        
        // 转换请求以处理图片
        ChatCompletionRequest processedRequest = processImagesInRequest(request);
        
        EventSource eventSource = httpClient.postStream("/chat/completions", processedRequest, new EventSourceListener() {
            private volatile boolean isDone = false;
            
            @Override
            public void onOpen(EventSource eventSource, okhttp3.Response response) {
                if (!response.isSuccessful()) {
                    isDone = true;
                    eventSource.cancel();
                    if (onError != null) {
                        String errorMessage = "Gemini流式请求失败 (状态码: " + response.code() + ")";
                        if (response.message() != null && !response.message().isEmpty()) {
                            errorMessage += " - " + response.message();
                        }
                        
                        logger.error("Gemini API 流式请求错误 - 状态码: {}, URL: {}", 
                            response.code(), response.request().url());
                        
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
                    eventSource.cancel();
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
                    eventSource.cancel();
                    logger.error("解析Gemini流式响应失败: {}", data, e);
                    if (onError != null) {
                        onError.accept(new OpenAIException("无法解析Gemini流式响应: " + data, e));
                    }
                }
            }
            
            @Override
            public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                if (!isDone) {
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
                        String errorMessage = "Gemini流式请求失败";
                        if (response != null) {
                            errorMessage += " (状态码: " + response.code() + ")";
                            if (response.message() != null && !response.message().isEmpty()) {
                                errorMessage += " - " + response.message();
                            }
                            
                            logger.error("Gemini API 流式请求失败 - 状态码: {}, URL: {}", 
                                response.code(), response.request().url(), t);
                        }
                        if (t != null && t.getMessage() != null) {
                            errorMessage += ": " + t.getMessage();
                        }
                        onError.accept(new OpenAIException(errorMessage, t));
                    }
                }
                eventSource.cancel();
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
     * 处理请求中的图片，将URL转换为base64编码
     * @param request 原始请求
     * @return 处理后的请求
     */
    private ChatCompletionRequest processImagesInRequest(ChatCompletionRequest request) {
        try {
            // 深拷贝请求对象
            String requestJson = objectMapper.writeValueAsString(request);
            ChatCompletionRequest processedRequest = objectMapper.readValue(requestJson, ChatCompletionRequest.class);
            
            // 首先收集所有需要下载的URL
            List<String> urlsToDownload = new ArrayList<>();
            Map<String, Map<String, Object>> urlToImageUrlMap = new HashMap<>();
            
            for (ChatMessage message : processedRequest.getMessages()) {
                if (message.getContent() instanceof List) {
                    List<Map<String, Object>> contentList = (List<Map<String, Object>>) message.getContent();
                    for (Map<String, Object> contentItem : contentList) {
                        if ("image_url".equals(contentItem.get("type"))) {
                            Map<String, Object> imageUrl = (Map<String, Object>) contentItem.get("image_url");
                            if (imageUrl != null && imageUrl.containsKey("url")) {
                                String url = (String) imageUrl.get("url");
                                // 如果是URL而不是base64，则加入下载列表
                                if (!url.startsWith("data:image")) {
                                    urlsToDownload.add(url);
                                    urlToImageUrlMap.put(url, imageUrl);
                                }
                            }
                        }
                    }
                }
            }
            
            // 并发批量下载所有图片
            if (!urlsToDownload.isEmpty()) {
                logger.info("批量下载 {} 张图片用于Gemini API", urlsToDownload.size());
                Map<String, String> downloadedImages = ImageUtils.downloadAndConvertBatch(urlsToDownload);
                
                // 更新所有图片URL为base64编码
                for (Map.Entry<String, String> entry : downloadedImages.entrySet()) {
                    String url = entry.getKey();
                    String base64Data = entry.getValue();
                    Map<String, Object> imageUrl = urlToImageUrlMap.get(url);
                    
                    if (base64Data != null && imageUrl != null) {
                        imageUrl.put("url", base64Data);
                        logger.debug("成功将图片URL转换为base64编码: {}", url);
                    } else if (base64Data == null) {
                        logger.error("下载图片失败: {}", url);
                        throw new OpenAIException("处理图片失败: 无法下载图片 " + url);
                    }
                }
            }
            
            return processedRequest;
        } catch (Exception e) {
            logger.error("处理请求中的图片失败", e);
            // 如果处理失败，返回原始请求
            return request;
        }
    }
}