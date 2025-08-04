package io.github.twwch.openai.sdk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.twwch.openai.sdk.BedrockConfig;
import io.github.twwch.openai.sdk.exception.OpenAIException;
import io.github.twwch.openai.sdk.model.ModelInfo;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.service.bedrock.BedrockModelAdapter;
import io.github.twwch.openai.sdk.service.bedrock.BedrockModelAdapterFactory;
import io.github.twwch.openai.sdk.service.bedrock.BedrockRequestValidator;
import software.amazon.awssdk.auth.credentials.*;
import io.github.twwch.openai.sdk.service.bedrock.auth.BedrockApiKeyCredentialsProvider;
import io.github.twwch.openai.sdk.service.bedrock.auth.BedrockCredentialsIsolator;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bedrock服务实现类
 */
public class BedrockService {
    private static final Logger logger = LoggerFactory.getLogger(BedrockService.class);
    
    private final BedrockConfig config;
    private final BedrockRuntimeClient client;
    private final BedrockRuntimeAsyncClient asyncClient;
    private final ObjectMapper objectMapper;
    private final BedrockModelAdapter modelAdapter;

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
        try {
            // 验证和清理请求参数
            BedrockRequestValidator.validateAndCleanRequest(request);
            
            // 使用配置的模型ID覆盖请求中的模型
            String modelId = config.getModelId();
            
            // 转换请求格式
            String bedrockRequest = modelAdapter.convertRequest(request, objectMapper);
            
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
            throw new OpenAIException("Bedrock请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建聊天完成（流式）
     */
    public void createChatCompletionStream(ChatCompletionRequest request,
                                          Consumer<ChatCompletionChunk> onChunk,
                                          Runnable onComplete,
                                          Consumer<Throwable> onError) throws OpenAIException {
        try {
            // 验证和清理请求参数
            BedrockRequestValidator.validateAndCleanRequest(request);
            
            // 使用配置的模型ID覆盖请求中的模型
            String modelId = config.getModelId();
            
            // 保存 tool_choice 信息，用于处理流式响应
            final Object toolChoice = request.getToolChoice();
            final List<ChatCompletionRequest.Tool> tools = request.getTools();
            
            // 转换请求格式（流式）
            String bedrockRequest = modelAdapter.convertStreamRequest(request, objectMapper);
            
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
            
            // 创建一个标志来跟踪是否已经发送了工具调用
            final boolean[] toolCallSent = {false};
            
            // 处理流式响应
            InvokeModelWithResponseStreamResponseHandler responseHandler = InvokeModelWithResponseStreamResponseHandler.builder()
                    .subscriber(responseStream -> {
                        if (responseStream instanceof PayloadPart) {
                            PayloadPart payloadPart = (PayloadPart) responseStream;
                            String chunk = payloadPart.bytes().asUtf8String();
                            
                            
                            try {
                                // 转换并发送chunk
                                List<ChatCompletionChunk> chunks = modelAdapter.convertStreamChunk(chunk, objectMapper);
                                for (ChatCompletionChunk completionChunk : chunks) {
                                    // 检查是否是 tool_use 结束但没有工具调用数据
                                    if (!toolCallSent[0] && 
                                        completionChunk.getChoices() != null && 
                                        !completionChunk.getChoices().isEmpty() &&
                                        "tool_use".equals(completionChunk.getChoices().get(0).getFinishReason()) &&
                                        toolChoice != null && tools != null) {
                                        
                                        // 基于 tool_choice 推断工具调用
                                        ChatCompletionChunk.Delta delta = completionChunk.getChoices().get(0).getDelta();
                                        if (delta != null && (delta.getToolCalls() == null || delta.getToolCalls().isEmpty())) {
                                            String toolName = extractToolName(toolChoice);
                                            if (toolName != null) {
                                                logger.debug("基于tool_choice推断工具调用: {}", toolName);
                                                
                                                List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
                                                ChatMessage.ToolCall toolCall = new ChatMessage.ToolCall();
                                                toolCall.setId("inferred-" + UUID.randomUUID().toString());
                                                toolCall.setType("function");
                                                
                                                ChatMessage.ToolCall.Function function = new ChatMessage.ToolCall.Function();
                                                function.setName(toolName);
                                                function.setArguments("{}"); // 空参数
                                                
                                                toolCall.setFunction(function);
                                                toolCalls.add(toolCall);
                                                delta.setToolCalls(toolCalls);
                                                
                                                toolCallSent[0] = true;
                                            }
                                        }
                                    }
                                    
                                    if (onChunk != null) {
                                        onChunk.accept(completionChunk);
                                    }
                                }
                            } catch (Exception e) {
                                logger.error("解析流式响应失败", e);
                                if (onError != null) {
                                    onError.accept(new OpenAIException("解析流式响应失败: " + e.getMessage(), e));
                                }
                            }
                        }
                    })
                    .onComplete(() -> {
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    })
                    .onError(throwable -> {
                        logger.error("Bedrock流式请求失败", throwable);
                        if (onError != null) {
                            onError.accept(new OpenAIException("Bedrock流式请求失败: " + throwable.getMessage(), throwable));
                        }
                    })
                    .build();
            
            asyncClient.invokeModelWithResponseStream(invokeRequest, responseHandler);
            
        } catch (Exception e) {
            logger.error("Bedrock流式请求失败", e);
            if (onError != null) {
                onError.accept(new OpenAIException("Bedrock流式请求失败: " + e.getMessage(), e));
            }
        }
    }
    
    /**
     * 从 tool_choice 中提取工具名称
     */
    private String extractToolName(Object toolChoice) {
        if (toolChoice instanceof String) {
            String choice = (String) toolChoice;
            // 忽略特殊的tool_choice值
            if ("auto".equals(choice) || "none".equals(choice) || "required".equals(choice)) {
                return null;
            }
            return choice;
        } else if (toolChoice instanceof Map) {
            Map<String, Object> tcMap = (Map<String, Object>) toolChoice;
            // OpenAI 格式: {"type": "function", "function": {"name": "tool_name"}}
            if (tcMap.containsKey("function")) {
                Map<String, Object> functionMap = (Map<String, Object>) tcMap.get("function");
                if (functionMap != null && functionMap.containsKey("name")) {
                    return (String) functionMap.get("name");
                }
            }
            // 直接格式: {"type": "tool", "name": "tool_name"}
            else if (tcMap.containsKey("name")) {
                return (String) tcMap.get("name");
            }
        }
        return null;
    }
}