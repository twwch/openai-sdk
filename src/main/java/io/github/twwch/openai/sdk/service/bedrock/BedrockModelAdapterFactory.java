package io.github.twwch.openai.sdk.service.bedrock;

import io.github.twwch.openai.sdk.exception.OpenAIException;

/**
 * Bedrock模型适配器工厂
 */
public class BedrockModelAdapterFactory {
    
    /**
     * 根据模型ID创建适配器
     */
    public static BedrockModelAdapter createAdapter(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            throw new OpenAIException("模型ID不能为空");
        }
        
        // Claude模型
        if (modelId.contains("anthropic.claude")) {
            return new ClaudeModelAdapter();
        }
        
        // Llama模型
        if (modelId.contains("meta.llama")) {
            return new LlamaModelAdapter();
        }
        
        // Amazon Titan模型
        if (modelId.contains("amazon.titan")) {
            return new TitanModelAdapter();
        }
        
        // AI21 Jurassic模型
        if (modelId.contains("ai21.j2")) {
            return new JurassicModelAdapter();
        }
        
        // Cohere模型
        if (modelId.contains("cohere.")) {
            return new CohereModelAdapter();
        }
        
        throw new OpenAIException("不支持的Bedrock模型: " + modelId);
    }
}