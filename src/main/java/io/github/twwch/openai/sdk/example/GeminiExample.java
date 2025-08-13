package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini API使用示例
 * 展示如何使用OpenAI兼容接口访问Gemini模型
 */
public class GeminiExample {
    
    public static void main(String[] args) {
        // 从环境变量获取API密钥
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("请设置环境变量 GEMINI_API_KEY");
            System.err.println("获取API密钥: https://ai.google.dev/");
            return;
        }
        
        try {
            // 创建Gemini客户端
            OpenAI client = OpenAI.gemini(apiKey);
            
            // 示例1: 简单文本对话
//            System.out.println("=== 示例1: 简单文本对话 ===");
//            simpleChat(client);
//
//            // 示例2: 带系统提示的对话
//            System.out.println("\n=== 示例2: 带系统提示的对话 ===");
//            chatWithSystemPrompt(client);
//
//            // 示例3: 多轮对话
//            System.out.println("\n=== 示例3: 多轮对话 ===");
//            multiTurnChat(client);
//
//            // 示例4: 流式输出
//            System.out.println("\n=== 示例4: 流式输出 ===");
//            streamingChat(client);
            
            // 示例5: 图片理解（多模态）
            System.out.println("\n=== 示例5: 图片理解 ===");
            imageUnderstanding(client);
            
        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 简单文本对话示例
     */
    private static void simpleChat(OpenAI client) throws Exception {
        String response = client.chat("gemini-2.0-flash", "什么是人工智能？请用简单的语言解释。");
        System.out.println("Gemini回复: " + response);
    }
    
    /**
     * 带系统提示的对话示例
     */
    private static void chatWithSystemPrompt(OpenAI client) throws Exception {
        String systemPrompt = "你是一个友好的助手，回答问题时要简洁明了。";
        String userPrompt = "解释一下什么是机器学习。";
        
        String response = client.chat("gemini-2.0-flash", systemPrompt, userPrompt);
        System.out.println("Gemini回复: " + response);
    }
    
    /**
     * 多轮对话示例
     */
    private static void multiTurnChat(OpenAI client) throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 第一轮对话
        messages.add(ChatMessage.user("我想学习编程，应该从哪种语言开始？"));
        ChatCompletionResponse response1 = client.createChatCompletion("gemini-2.0-flash", messages);
        System.out.println("用户: 我想学习编程，应该从哪种语言开始？");
        System.out.println("Gemini: " + response1.getContent());
        
        // 添加助手回复到对话历史
        messages.add(ChatMessage.assistant(response1.getContent()));
        
        // 第二轮对话
        messages.add(ChatMessage.user("为什么推荐这个语言？"));
        ChatCompletionResponse response2 = client.createChatCompletion("gemini-2.0-flash", messages);
        System.out.println("\n用户: 为什么推荐这个语言？");
        System.out.println("Gemini: " + response2.getContent());
    }
    
    /**
     * 流式输出示例
     */
    private static void streamingChat(OpenAI client) throws Exception {
        System.out.print("Gemini正在思考: ");
        
        client.chatStream("gemini-2.0-flash", 
            "写一首关于春天的短诗", 
            chunk -> {
                System.out.print(chunk);
                System.out.flush();
            });
        
        System.out.println();
    }
    
    /**
     * 图片理解示例（多模态）
     */
    private static void imageUnderstanding(OpenAI client) throws Exception {
        // 创建包含图片的消息
        List<Map<String, Object>> content = new ArrayList<>();
        
        // 添加文本
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", "这张图片中有什么？请详细描述。");
        content.add(textContent);
        
        // 添加图片URL（可以是网络URL或base64编码）
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, Object> imageUrl = new HashMap<>();
        // 这里使用一个示例图片URL，实际使用时替换为真实的图片URL
        imageUrl.put("url", "https://cdn-aws.iweaver.ai/docx/2025/08/13/6cfd1b07-cf24-4146-8721-52c409a5d084/20250618-191336.png");
        // 或者使用base64编码的图片
        // imageUrl.put("url", "data:image/jpeg;base64,/9j/4AAQSkZJRg...");
        imageContent.put("image_url", imageUrl);
        content.add(imageContent);
        
        // 创建消息
        ChatMessage message = new ChatMessage("user", content);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(message);
        
        // 创建请求
        ChatCompletionRequest request = new ChatCompletionRequest("gemini-2.0-flash-002", messages);
        
        // 发送请求
        ChatCompletionResponse response = client.createChatCompletion(request);
        System.out.println("Gemini分析图片: " + response.getContent());
        
        System.out.println("\n注意: 图片理解功能需要提供有效的图片URL或base64编码的图片数据。");
        System.out.println("Gemini会自动将URL下载并转换为base64格式发送给API。");
    }
}