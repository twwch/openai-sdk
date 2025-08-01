package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.exception.OpenAIException;
import io.github.twwch.openai.sdk.model.ModelInfo;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.service.OpenAIService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * OpenAI客户端
 * 这是SDK的主要入口点
 */
public class OpenAI {
    private final OpenAIService service;

    /**
     * 使用API密钥创建OpenAI客户端
     * @param apiKey OpenAI API密钥
     */
    public OpenAI(String apiKey) {
        this(new OpenAIConfig(apiKey));
    }

    /**
     * 使用API密钥和自定义基础URL创建OpenAI客户端
     * @param apiKey OpenAI API密钥
     * @param baseUrl API基础URL
     */
    public OpenAI(String apiKey, String baseUrl) {
        this(new OpenAIConfig(apiKey, baseUrl));
    }
    
    /**
     * 创建Azure OpenAI客户端
     * @param apiKey Azure OpenAI API密钥
     * @param resourceName Azure资源名称
     * @param deploymentId 部署ID
     * @return Azure OpenAI客户端
     */
    public static OpenAI azure(String apiKey, String resourceName, String deploymentId) {
        return new OpenAI(new AzureOpenAIConfig(apiKey, resourceName, deploymentId));
    }
    
    /**
     * 创建Azure OpenAI客户端（带API版本）
     * @param apiKey Azure OpenAI API密钥
     * @param resourceName Azure资源名称
     * @param deploymentId 部署ID
     * @param apiVersion API版本
     * @return Azure OpenAI客户端
     */
    public static OpenAI azure(String apiKey, String resourceName, String deploymentId, String apiVersion) {
        return new OpenAI(new AzureOpenAIConfig(apiKey, resourceName, deploymentId, apiVersion));
    }
    
    /**
     * 创建Bedrock客户端（使用默认凭证）
     * @param region AWS区域
     * @param modelId Bedrock模型ID
     * @return Bedrock客户端
     */
    public static OpenAI bedrock(String region, String modelId) {
        return new OpenAI(new BedrockConfig(region, modelId));
    }
    
    /**
     * 创建Bedrock客户端（使用访问密钥）
     * @param region AWS区域
     * @param accessKeyId AWS访问密钥ID
     * @param secretAccessKey AWS密钥
     * @param modelId Bedrock模型ID
     * @return Bedrock客户端
     */
    public static OpenAI bedrock(String region, String accessKeyId, String secretAccessKey, String modelId) {
        return new OpenAI(new BedrockConfig(region, accessKeyId, secretAccessKey, modelId));
    }
    
    /**
     * 创建Bedrock客户端（使用临时凭证）
     * @param region AWS区域
     * @param accessKeyId AWS访问密钥ID
     * @param secretAccessKey AWS密钥
     * @param sessionToken 会话令牌
     * @param modelId Bedrock模型ID
     * @return Bedrock客户端
     */
    public static OpenAI bedrock(String region, String accessKeyId, String secretAccessKey, String sessionToken, String modelId) {
        return new OpenAI(new BedrockConfig(region, accessKeyId, secretAccessKey, sessionToken, modelId));
    }

    /**
     * 使用配置创建OpenAI客户端
     * @param config OpenAI配置
     */
    public OpenAI(OpenAIConfig config) {
        this.service = new OpenAIService(config);
    }

    /**
     * 获取可用模型列表
     * @return 模型列表
     * @throws OpenAIException 如果请求失败
     */
    public List<ModelInfo> listModels() throws OpenAIException {
        return service.listModels();
    }

    /**
     * 获取模型详情
     * @param modelId 模型ID
     * @return 模型详情
     * @throws OpenAIException 如果请求失败
     */
    public ModelInfo getModel(String modelId) throws OpenAIException {
        return service.getModel(modelId);
    }

    /**
     * 创建聊天完成
     * @param request 聊天完成请求
     * @return 聊天完成响应
     * @throws OpenAIException 如果请求失败
     */
    public ChatCompletionResponse createChatCompletion(ChatCompletionRequest request) throws OpenAIException {
        return service.createChatCompletion(request);
    }

    /**
     * 创建聊天完成（简化版）
     * @param model 模型ID
     * @param messages 消息列表
     * @return 聊天完成响应
     * @throws OpenAIException 如果请求失败
     */
    public ChatCompletionResponse createChatCompletion(String model, List<ChatMessage> messages) throws OpenAIException {
        ChatCompletionRequest request = new ChatCompletionRequest(model, messages);
        return createChatCompletion(request);
    }

    /**
     * 创建聊天完成（简化版）
     * @param model 模型ID
     * @param messages 消息数组
     * @return 聊天完成响应
     * @throws OpenAIException 如果请求失败
     */
    public ChatCompletionResponse createChatCompletion(String model, ChatMessage... messages) throws OpenAIException {
        return createChatCompletion(model, Arrays.asList(messages));
    }

    /**
     * 创建聊天完成（最简化版）
     * @param model 模型ID
     * @param prompt 用户提示
     * @return 聊天完成响应
     * @throws OpenAIException 如果请求失败
     */
    public ChatCompletionResponse createChatCompletion(String model, String prompt) throws OpenAIException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(prompt));
        return createChatCompletion(model, messages);
    }

    /**
     * 创建聊天完成（最简化版，带系统提示）
     * @param model 模型ID
     * @param systemPrompt 系统提示
     * @param userPrompt 用户提示
     * @return 聊天完成响应
     * @throws OpenAIException 如果请求失败
     */
    public ChatCompletionResponse createChatCompletion(String model, String systemPrompt, String userPrompt) throws OpenAIException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(userPrompt));
        return createChatCompletion(model, messages);
    }

    /**
     * 获取聊天完成内容（简化版）
     * @param model 模型ID
     * @param prompt 用户提示
     * @return 聊天完成内容
     * @throws OpenAIException 如果请求失败
     */
    public String chat(String model, String prompt) throws OpenAIException {
        ChatCompletionResponse response = createChatCompletion(model, prompt);
        return response.getContent();
    }

    /**
     * 获取聊天完成内容（简化版，带系统提示）
     * @param model 模型ID
     * @param systemPrompt 系统提示
     * @param userPrompt 用户提示
     * @return 聊天完成内容
     * @throws OpenAIException 如果请求失败
     */
    public String chat(String model, String systemPrompt, String userPrompt) throws OpenAIException {
        ChatCompletionResponse response = createChatCompletion(model, systemPrompt, userPrompt);
        return response.getContent();
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
        service.createChatCompletionStream(request, onChunk, onComplete, onError);
    }

    /**
     * 创建聊天完成（流式简化版）
     * @param model 模型ID
     * @param messages 消息列表
     * @param onChunk 处理每个数据块的回调
     * @throws OpenAIException 如果请求失败
     */
    public void createChatCompletionStream(String model, List<ChatMessage> messages,
                                          Consumer<String> onChunk) throws OpenAIException {
        CountDownLatch latch = new CountDownLatch(1);
        ChatCompletionRequest request = new ChatCompletionRequest(model, messages);
        createChatCompletionStream(request, 
            chunk -> {
                String content = chunk.getContent();
                if (content != null) {
                    onChunk.accept(content);
                }
            },
            () -> latch.countDown(),
            e -> {
                latch.countDown();
                throw new RuntimeException("流式请求失败", e);
            }
        );
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenAIException("流式请求被中断", e);
        }
    }

    /**
     * 流式聊天（最简化版）
     * @param model 模型ID
     * @param prompt 用户提示
     * @param onChunk 处理每个数据块的回调
     * @throws OpenAIException 如果请求失败
     */
    public void chatStream(String model, String prompt, Consumer<String> onChunk) throws OpenAIException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(prompt));
        createChatCompletionStream(model, messages, onChunk);
    }

    /**
     * 流式聊天（带系统提示）
     * @param model 模型ID
     * @param systemPrompt 系统提示
     * @param userPrompt 用户提示
     * @param onChunk 处理每个数据块的回调
     * @throws OpenAIException 如果请求失败
     */
    public void chatStream(String model, String systemPrompt, String userPrompt,
                          Consumer<String> onChunk) throws OpenAIException {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(userPrompt));
        createChatCompletionStream(model, messages, onChunk);
    }
}