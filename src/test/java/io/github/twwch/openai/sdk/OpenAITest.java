package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAI SDK测试类
 * 注意：这些测试需要有效的API密钥才能运行，默认是禁用的
 */
public class OpenAITest {

    // 替换为你的API密钥进行测试
    private static final String API_KEY = "your-api-key";

    @Test
    @Disabled("需要有效的API密钥")
    public void testCreateChatCompletion() {
        OpenAI openai = new OpenAI(API_KEY);
        
        ChatCompletionResponse response = openai.createChatCompletion(
            "gpt-3.5-turbo", 
            ChatMessage.user("你好，请用一句话介绍一下自己。")
        );
        
        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isEmpty());
        System.out.println("AI回复: " + response.getContent());
    }

    @Test
    @Disabled("需要有效的API密钥")
    public void testSimpleChatMethod() {
        OpenAI openai = new OpenAI(API_KEY);
        
        String response = openai.chat("gpt-3.5-turbo", "计算23+45等于多少？");
        
        assertNotNull(response);
        assertFalse(response.isEmpty());
        System.out.println("AI回复: " + response);
    }

    @Test
    @Disabled("需要有效的Azure API密钥")
    public void testAzureOpenAI() {
        OpenAI openai = OpenAI.azure(
            "your-azure-api-key",
            "your-resource-name",
            "your-deployment-id"
        );
        
        String response = openai.chat("gpt-3.5-turbo", "你好，请用一句话介绍一下自己。");
        
        assertNotNull(response);
        assertFalse(response.isEmpty());
        System.out.println("Azure AI回复: " + response);
    }
}