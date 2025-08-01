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
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bedrock服务实现类
 */
public class BedrockService {
    private final BedrockConfig config;
    private final BedrockRuntimeClient client;
    private final BedrockRuntimeAsyncClient asyncClient;
    private final ObjectMapper objectMapper;
    private final BedrockModelAdapter modelAdapter;

    public BedrockService(BedrockConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        
        // 创建AWS凭证提供者
        AwsCredentialsProvider credentialsProvider = createCredentialsProvider();
        
        // 创建Bedrock客户端
        this.client = BedrockRuntimeClient.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
                
        this.asyncClient = BedrockRuntimeAsyncClient.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
                
        // 创建模型适配器
        this.modelAdapter = BedrockModelAdapterFactory.createAdapter(config.getModelId());
    }

    private AwsCredentialsProvider createCredentialsProvider() {
        if (config.getAccessKeyId() != null && config.getSecretAccessKey() != null) {
            // 检查是否是 Bedrock API Key 格式
            if (config.getAccessKeyId().startsWith("BedrockAPIKey-")) {
                // 这是 Bedrock 特定的 API Key，需要特殊处理
                // 注意：AWS SDK 可能不直接支持这种格式，需要使用特定的认证方式
                System.err.println("警告：检测到 Bedrock API Key 格式，但当前可能需要标准 AWS 凭证");
            }
            
            if (config.getSessionToken() != null) {
                // 使用临时凭证
                return StaticCredentialsProvider.create(
                    AwsSessionCredentials.create(
                        config.getAccessKeyId(),
                        config.getSecretAccessKey(),
                        config.getSessionToken()
                    )
                );
            } else {
                // 使用永久凭证
                return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        config.getAccessKeyId(),
                        config.getSecretAccessKey()
                    )
                );
            }
        } else {
            // 使用默认凭证链
            return DefaultCredentialsProvider.create();
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
            // 使用配置的模型ID覆盖请求中的模型
            String modelId = config.getModelId();
            
            // 转换请求格式
            String bedrockRequest = modelAdapter.convertRequest(request, objectMapper);
            
            // 调试信息
            System.out.println("Bedrock请求长度: " + bedrockRequest.length() + " bytes");
            if (bedrockRequest.length() > 100000) {
                System.out.println("警告：请求体过大，可能超出限制");
            }
            // 只在调试模式下打印完整请求
            if (System.getProperty("bedrock.debug", "false").equals("true")) {
                System.out.println("Bedrock请求: " + bedrockRequest);
            }
            
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
            // 使用配置的模型ID覆盖请求中的模型
            String modelId = config.getModelId();
            
            // 转换请求格式（流式）
            String bedrockRequest = modelAdapter.convertStreamRequest(request, objectMapper);
            
            // 调试：打印请求长度
            System.out.println("Bedrock流式请求长度: " + bedrockRequest.length() + " bytes");
            if (bedrockRequest.length() > 100000) {
                System.out.println("警告：请求体过大，可能超出限制");
            }
            
            // 调用Bedrock流式API
            InvokeModelWithResponseStreamRequest invokeRequest = InvokeModelWithResponseStreamRequest.builder()
                    .modelId(modelId)
                    .body(SdkBytes.fromString(bedrockRequest, StandardCharsets.UTF_8))
                    .contentType("application/json")
                    .accept("application/json")
                    .build();
            
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
                                    if (onChunk != null) {
                                        onChunk.accept(completionChunk);
                                    }
                                }
                            } catch (Exception e) {
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
                        if (onError != null) {
                            onError.accept(new OpenAIException("Bedrock流式请求失败: " + throwable.getMessage(), throwable));
                        }
                    })
                    .build();
            
            asyncClient.invokeModelWithResponseStream(invokeRequest, responseHandler);
            
        } catch (Exception e) {
            if (onError != null) {
                onError.accept(new OpenAIException("Bedrock流式请求失败: " + e.getMessage(), e));
            }
        }
    }
}