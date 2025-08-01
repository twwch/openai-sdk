package io.github.twwch.openai.sdk.service.bedrock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;

import java.util.List;

/**
 * Bedrock模型适配器接口
 * 用于转换不同模型的请求和响应格式
 */
public interface BedrockModelAdapter {
    /**
     * 转换聊天请求为Bedrock格式
     */
    String convertRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception;
    
    /**
     * 转换流式聊天请求为Bedrock格式
     */
    String convertStreamRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception;
    
    /**
     * 转换Bedrock响应为OpenAI格式
     */
    ChatCompletionResponse convertResponse(String bedrockResponse, ChatCompletionRequest originalRequest, ObjectMapper objectMapper) throws Exception;
    
    /**
     * 转换Bedrock流式响应块为OpenAI格式
     */
    List<ChatCompletionChunk> convertStreamChunk(String chunk, ObjectMapper objectMapper) throws Exception;
    
    /**
     * 是否支持指定的模型
     */
    boolean supports(String modelId);
}