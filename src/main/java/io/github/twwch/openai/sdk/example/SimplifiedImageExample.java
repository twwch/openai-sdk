package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.Arrays;

/**
 * 简化的图片分析示例
 * 展示使用新的便捷方法创建包含图片的消息
 */
public class SimplifiedImageExample {
    
    public static void main(String[] args) {
        // 创建OpenAI客户端（可以是标准OpenAI、Azure或Bedrock）
        OpenAI client = createClient();
        
        if (client == null) {
            System.err.println("请配置API密钥");
            return;
        }
        
        // 示例1：最简单的用法 - 一段文本和一张图片
        example1SimpleImage(client);
        
        // 示例2：多张图片
        example2MultipleImages(client);
        
        // 示例3：使用ContentPart数组的高级用法
        example3AdvancedContent(client);
        
        // 示例4：混合使用不同创建方法
        example4MixedUsage(client);
    }
    
    /**
     * 示例1：最简单的用法 - 一段文本和一张图片
     */
    private static void example1SimpleImage(OpenAI client) {
        System.out.println("\n=== 示例1：简单图片分析 ===");
        
        try {
            // 使用便捷方法创建包含图片的消息
            ChatMessage message = ChatMessage.userWithImage(
                "这张图片里有什么？",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/320px-Cat03.jpg"
            );
            
            ChatCompletionResponse response = client.createChatCompletion(
                "gpt-4-vision-preview",  // 或其他支持视觉的模型
                Arrays.asList(message)
            );
            
            System.out.println("分析结果: " + response.getContent());
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
        }
    }
    
    /**
     * 示例2：多张图片
     */
    private static void example2MultipleImages(OpenAI client) {
        System.out.println("\n=== 示例2：多张图片比较 ===");
        
        try {
            // 使用便捷方法创建包含多张图片的消息
            ChatMessage message = ChatMessage.userWithImages(
                "请比较这两张图片的不同之处：",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/320px-Cat03.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4d/Cat_November_2010-1a.jpg/320px-Cat_November_2010-1a.jpg"
            );
            
            ChatCompletionResponse response = client.createChatCompletion(
                "gpt-4-vision-preview",
                Arrays.asList(message)
            );
            
            System.out.println("比较结果: " + response.getContent());
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
        }
    }
    
    /**
     * 示例3：使用ContentPart数组的高级用法
     */
    private static void example3AdvancedContent(OpenAI client) {
        System.out.println("\n=== 示例3：高级内容组合 ===");
        
        try {
            // 使用ContentPart数组创建复杂的多模态消息
            ChatMessage message = ChatMessage.user(
                ChatMessage.ContentPart.text("让我们分析一些图片："),
                ChatMessage.ContentPart.text("第一张图片："),
                ChatMessage.ContentPart.imageUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Gfp-wisconsin-madison-the-nature-boardwalk.jpg/320px-Gfp-wisconsin-madison-the-nature-boardwalk.jpg"),
                ChatMessage.ContentPart.text("第二张图片（base64）："),
                ChatMessage.ContentPart.imageUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg=="),
                ChatMessage.ContentPart.text("请描述两张图片的内容。")
            );
            
            ChatCompletionResponse response = client.createChatCompletion(
                "gpt-4-vision-preview",
                Arrays.asList(message)
            );
            
            System.out.println("分析结果: " + response.getContent());
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
        }
    }
    
    /**
     * 示例4：混合使用不同创建方法
     */
    private static void example4MixedUsage(OpenAI client) {
        System.out.println("\n=== 示例4：对话中的图片分析 ===");
        
        try {
            // 第一轮：发送图片
            ChatMessage userMsg1 = ChatMessage.userWithImage(
                "请记住这张猫的图片",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/320px-Cat03.jpg"
            );
            
            ChatCompletionResponse response1 = client.createChatCompletion(
                "gpt-4-vision-preview",
                Arrays.asList(userMsg1)
            );
            
            System.out.println("AI: " + response1.getContent());
            
            // 第二轮：纯文本对话
            ChatMessage userMsg2 = ChatMessage.user("这只猫是什么颜色的？");
            
            ChatCompletionResponse response2 = client.createChatCompletion(
                "gpt-4-vision-preview",
                Arrays.asList(
                    userMsg1,
                    ChatMessage.assistant(response1.getContent()),
                    userMsg2
                )
            );
            
            System.out.println("颜色分析: " + response2.getContent());
            
            // 第三轮：发送另一张图片比较
            ChatMessage userMsg3 = ChatMessage.userWithImage(
                "这只猫和刚才的猫有什么不同？",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4d/Cat_November_2010-1a.jpg/320px-Cat_November_2010-1a.jpg"
            );
            
            ChatCompletionResponse response3 = client.createChatCompletion(
                "gpt-4-vision-preview",
                Arrays.asList(
                    userMsg1,
                    ChatMessage.assistant(response1.getContent()),
                    userMsg2,
                    ChatMessage.assistant(response2.getContent()),
                    userMsg3
                )
            );
            
            System.out.println("对比分析: " + response3.getContent());
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
        }
    }
    
    /**
     * 根据环境变量创建相应的客户端
     */
    private static OpenAI createClient() {
        // 尝试OpenAI
        String openaiKey = System.getenv("OPENAI_API_KEY");
        if (openaiKey != null) {
            System.out.println("使用OpenAI API");
            return new OpenAI(openaiKey);
        }
        
        // 尝试Azure
        String azureKey = System.getenv("AZURE_OPENAI_API_KEY");
        String azureResource = System.getenv("AZURE_OPENAI_RESOURCE_NAME");
        if (azureKey != null && azureResource != null) {
            System.out.println("使用Azure OpenAI");
            return OpenAI.azure(azureKey, azureResource, "gpt-4-vision");
        }
        
        // 尝试Bedrock
        String awsKey = System.getenv("AWS_ACCESS_KEY_ID");
        String awsSecret = System.getenv("AWS_SECRET_ACCESS_KEY");
        if (awsKey != null && awsSecret != null) {
            System.out.println("使用AWS Bedrock");
            return OpenAI.bedrock("us-east-1", awsKey, awsSecret, 
                                  "anthropic.claude-3-sonnet-20240229-v1:0");
        }
        
        return null;
    }
}