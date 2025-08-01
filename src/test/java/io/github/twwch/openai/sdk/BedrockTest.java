package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.exception.OpenAIException;
import io.github.twwch.openai.sdk.model.ModelInfo;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.service.bedrock.BedrockModelAdapterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bedrock功能测试
 */
public class BedrockTest {
    
    @Test
    public void testBedrockConfig() {
        // 测试默认凭证配置
        BedrockConfig config1 = new BedrockConfig("us-east-1", "anthropic.claude-3-sonnet-20240229");
        assertEquals("us-east-1", config1.getRegion());
        assertEquals("anthropic.claude-3-sonnet-20240229", config1.getModelId());
        assertTrue(config1.isBedrock());
        assertFalse(config1.isAzure());
        
        // 测试访问密钥配置
        BedrockConfig config2 = new BedrockConfig("us-west-2", "test-key", "test-secret", "claude-v2");
        assertEquals("us-west-2", config2.getRegion());
        assertEquals("test-key", config2.getAccessKeyId());
        assertEquals("test-secret", config2.getSecretAccessKey());
        assertEquals("claude-v2", config2.getModelId());
        
        // 测试临时凭证配置
        BedrockConfig config3 = new BedrockConfig("eu-west-1", "key", "secret", "token", "titan");
        assertEquals("token", config3.getSessionToken());
    }
    
    @Test
    public void testOpenAIBedrockIntegration() {
        // 测试通过OpenAI.bedrock()创建客户端
        //key 从环境变量获取AWS_BEARER_KEY_BEDROCK
        String key = System.getenv("AWS_BEARER_KEY_BEDROCK");
        // secret  从环境变量获取AWS_BEARER_TOKEN_BEDROCK
        String secret = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
        OpenAI client1 = OpenAI.bedrock("us-east-1", "anthropic.claude-3-sonnet-20240229");
        assertNotNull(client1);
        
        OpenAI client2 = OpenAI.bedrock("us-west-2", key, secret, "anthropic.claude-v2");
        assertNotNull(client2);
        
//        OpenAI client3 = OpenAI.bedrock("eu-west-1", key, secret, "token", "amazon.titan-text-express-v1");
//        assertNotNull(client3);
    }
    
    @Test
    public void testModelAdapterFactory() {
        // 测试模型适配器工厂
        var adapter1 = BedrockModelAdapterFactory.createAdapter("anthropic.claude-3-opus-20240229");
        assertTrue(adapter1.supports("anthropic.claude-3-opus-20240229"));
        
        var adapter2 = BedrockModelAdapterFactory.createAdapter("meta.llama2-70b-chat-v1");
        assertTrue(adapter2.supports("meta.llama2-70b-chat-v1"));
        
        var adapter3 = BedrockModelAdapterFactory.createAdapter("amazon.titan-text-express-v1");
        assertTrue(adapter3.supports("amazon.titan-text-express-v1"));
        
        // 测试不支持的模型
        assertThrows(OpenAIException.class, () -> {
            BedrockModelAdapterFactory.createAdapter("unknown.model");
        });
    }
    
    @Test
    public void testChatMessageHelpers() {
        // 测试ChatMessage辅助方法
        ChatMessage systemMsg = ChatMessage.system("You are a helpful assistant.");
        assertEquals("system", systemMsg.getRole());
        assertEquals("You are a helpful assistant.", systemMsg.getContent());
        
        ChatMessage userMsg = ChatMessage.user("Hello!");
        assertEquals("user", userMsg.getRole());
        assertEquals("Hello!", userMsg.getContent());
        
        ChatMessage assistantMsg = ChatMessage.assistant("Hi there!");
        assertEquals("assistant", assistantMsg.getRole());
        assertEquals("Hi there!", assistantMsg.getContent());
    }
    
    /**
     * 注意：以下测试需要有效的AWS凭证才能运行
     * 可以通过设置环境变量或AWS配置文件提供凭证
     */
    //@Test
    public void testListModelsWithRealAWS() {
        OpenAI client = OpenAI.bedrock("us-east-1", "anthropic.claude-3-sonnet-20240229");
        
        try {
            List<ModelInfo> models = client.listModels();
            assertNotNull(models);
            assertFalse(models.isEmpty());
            
            // 验证包含预期的模型
            boolean hasClaude = models.stream().anyMatch(m -> m.getId().contains("claude"));
            assertTrue(hasClaude);
            
        } catch (OpenAIException e) {
            // 如果没有配置AWS凭证，测试将失败
            System.out.println("跳过真实AWS测试: " + e.getMessage());
        }
    }
}