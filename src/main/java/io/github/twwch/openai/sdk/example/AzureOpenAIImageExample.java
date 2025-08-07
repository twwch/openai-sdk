package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.Arrays;

/**
 * Azure OpenAI 图片分析示例
 * 演示如何使用支持视觉功能的模型（如GPT-4 Vision）来分析图片
 */
public class AzureOpenAIImageExample {
    public static void main(String[] args) {
        // Azure OpenAI配置
        String apiKey = System.getenv("AZURE_OPENAI_API_KEY");
        String resourceName = System.getenv("AZURE_OPENAI_RESOURCE_NAME");
        String deploymentId = "gpt-4o-mini"; // 需要部署支持视觉的模型
        
        if (apiKey == null || resourceName == null) {
            System.err.println("请设置环境变量 AZURE_OPENAI_API_KEY 和 AZURE_OPENAI_RESOURCE_NAME");
            return;
        }
        
        // 创建Azure OpenAI客户端
        OpenAI openai = OpenAI.azure(apiKey, resourceName, deploymentId);
        
        // 示例1：分析URL图片
        testUrlImage(openai);
        
        // 示例2：分析Base64编码的图片
        testBase64Image(openai);
        
        // 示例3：分析多张图片并比较
        testMultipleImages(openai);
    }
    
    /**
     * 示例1：分析URL图片
     */
    private static void testUrlImage(OpenAI openai) {
        System.out.println("\n=== 示例1：分析URL图片 ===");
        
        // 创建包含图片的消息
        ChatMessage.ContentPart[] parts = new ChatMessage.ContentPart[] {
            ChatMessage.ContentPart.text("请描述这张图片中的内容，包括主要对象、颜色和场景。"),
            ChatMessage.ContentPart.imageUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Gfp-wisconsin-madison-the-nature-boardwalk.jpg/320px-Gfp-wisconsin-madison-the-nature-boardwalk.jpg")
        };
        
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(parts);
        
        try {
            // 发送请求
            ChatCompletionResponse response = openai.createChatCompletion(
                "gpt-4o-mini",  // 在Azure中这个参数会被忽略，使用的是deploymentId
                Arrays.asList(message)
            );
            
            System.out.println("AI分析结果: " + response.getContent());
            System.out.println("使用的tokens: " + response.getUsage().getTotalTokens());
        } catch (Exception e) {
            System.err.println("分析失败: " + e.getMessage());
        }
    }
    
    /**
     * 示例2：分析Base64编码的图片
     */
    private static void testBase64Image(OpenAI openai) {
        System.out.println("\n=== 示例2：分析Base64图片 ===");
        
        // 使用一个简单的1x1像素的红色图片作为示例
        String base64Image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        
        ChatMessage.ContentPart[] parts = new ChatMessage.ContentPart[] {
            ChatMessage.ContentPart.text("这是什么颜色的像素？"),
            ChatMessage.ContentPart.imageUrl(base64Image)
        };
        
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(parts);
        
        try {
            ChatCompletionResponse response = openai.createChatCompletion(
                "gpt-4o-mini",
                Arrays.asList(
                    ChatMessage.system("你是一个图像分析专家，请准确描述图片内容。"),
                    message
                )
            );
            
            System.out.println("AI分析结果: " + response.getContent());
        } catch (Exception e) {
            System.err.println("分析失败: " + e.getMessage());
        }
    }
    
    /**
     * 示例3：分析多张图片并比较
     */
    private static void testMultipleImages(OpenAI openai) {
        System.out.println("\n=== 示例3：比较多张图片 ===");
        
        // 创建包含多张图片的消息
        ChatMessage.ContentPart[] parts = new ChatMessage.ContentPart[] {
            ChatMessage.ContentPart.text("请比较以下两张图片："),
            ChatMessage.ContentPart.text("图片1："),
            ChatMessage.ContentPart.imageUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/320px-Cat03.jpg"),
            ChatMessage.ContentPart.text("图片2："),
            ChatMessage.ContentPart.imageUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/4/4d/Cat_November_2010-1a.jpg/320px-Cat_November_2010-1a.jpg"),
            ChatMessage.ContentPart.text("请说明它们的相似之处和不同之处。")
        };
        
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(parts);
        
        try {
            // 创建对话历史
            ChatCompletionResponse response = openai.createChatCompletion(
                "gpt-4o-mini",
                Arrays.asList(
                    ChatMessage.system("你是一个专业的图像比较分析师。请详细对比图片的内容、颜色、构图等方面。"),
                    message
                )
            );
            
            System.out.println("比较结果: " + response.getContent());
            
            // 继续对话
            ChatMessage followUp = ChatMessage.user("这些猫的品种可能是什么？");
            
            response = openai.createChatCompletion(
                "gpt-4o-mini",
                Arrays.asList(
                    ChatMessage.system("你是一个专业的图像比较分析师。请详细对比图片的内容、颜色、构图等方面。"),
                    message,
                    ChatMessage.assistant(response.getContent()),
                    followUp
                )
            );
            
            System.out.println("\n品种分析: " + response.getContent());
            
        } catch (Exception e) {
            System.err.println("比较失败: " + e.getMessage());
        }
    }
}