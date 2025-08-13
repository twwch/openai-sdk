package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Gemini图片处理示例
 * 展示如何使用Gemini API处理图片，包括URL和本地文件
 */
public class GeminiImageExample {
    
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
            
            // 示例1: 分析网络图片
            System.out.println("=== 示例1: 分析网络图片 ===");
            analyzeImageFromUrl(client);
            
            // 示例2: 分析本地图片
            System.out.println("\n=== 示例2: 分析本地图片 ===");
            analyzeLocalImage(client);
            
            // 示例3: 多图片比较
            System.out.println("\n=== 示例3: 多图片比较 ===");
            compareMultipleImages(client);
            
            // 示例4: 图片问答
            System.out.println("\n=== 示例4: 图片问答 ===");
            imageQA(client);
            
        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 分析网络图片
     */
    private static void analyzeImageFromUrl(OpenAI client) throws Exception {
        // 创建包含图片URL的消息
        List<Map<String, Object>> content = new ArrayList<>();
        
        // 添加文本提示
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", "请详细描述这张图片的内容，包括主要元素、颜色、构图等。");
        content.add(textContent);
        
        // 添加图片URL（使用一个公开的示例图片）
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, Object> imageUrl = new HashMap<>();
        // 使用一个公开的示例图片URL（这里是一个占位符，实际使用时需要替换）
        imageUrl.put("url", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/240px-PNG_transparency_demonstration_1.png");
        imageContent.put("image_url", imageUrl);
        content.add(imageContent);
        
        // 创建消息并发送请求
        ChatMessage message = new ChatMessage("user", content);
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gemini-2.0-flash",
            Arrays.asList(message)
        );
        
        ChatCompletionResponse response = client.createChatCompletion(request);
        System.out.println("Gemini分析结果: " + response.getContent());
    }
    
    /**
     * 分析本地图片
     */
    private static void analyzeLocalImage(OpenAI client) throws Exception {
        // 检查是否有本地图片文件
        Path imagePath = Paths.get("test-image.jpg");
        if (!Files.exists(imagePath)) {
            System.out.println("未找到本地图片文件 test-image.jpg");
            System.out.println("请在当前目录下放置一个名为 test-image.jpg 的图片文件");
            return;
        }
        
        // 读取图片并转换为base64
        byte[] imageBytes = Files.readAllBytes(imagePath);
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:image/jpeg;base64," + base64Image;
        
        // 创建包含base64图片的消息
        List<Map<String, Object>> content = new ArrayList<>();
        
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", "这是什么图片？请描述图片中的内容。");
        content.add(textContent);
        
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, Object> imageUrl = new HashMap<>();
        imageUrl.put("url", dataUrl);
        imageContent.put("image_url", imageUrl);
        content.add(imageContent);
        
        // 发送请求
        ChatMessage message = new ChatMessage("user", content);
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gemini-2.0-flash",
            Arrays.asList(message)
        );
        
        ChatCompletionResponse response = client.createChatCompletion(request);
        System.out.println("Gemini分析结果: " + response.getContent());
    }
    
    /**
     * 比较多张图片
     */
    private static void compareMultipleImages(OpenAI client) throws Exception {
        // 创建包含多张图片的消息
        List<Map<String, Object>> content = new ArrayList<>();
        
        // 添加文本提示
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", "请比较这两张图片的相似之处和不同之处。");
        content.add(textContent);
        
        // 添加第一张图片（示例URL）
        Map<String, Object> image1Content = new HashMap<>();
        image1Content.put("type", "image_url");
        Map<String, Object> image1Url = new HashMap<>();
        image1Url.put("url", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/240px-PNG_transparency_demonstration_1.png");
        image1Content.put("image_url", image1Url);
        content.add(image1Content);
        
        // 添加第二张图片（示例URL）
        Map<String, Object> image2Content = new HashMap<>();
        image2Content.put("type", "image_url");
        Map<String, Object> image2Url = new HashMap<>();
        image2Url.put("url", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ec/Mona_Lisa%2C_by_Leonardo_da_Vinci%2C_from_C2RMF_retouched.jpg/240px-Mona_Lisa%2C_by_Leonardo_da_Vinci%2C_from_C2RMF_retouched.jpg");
        image2Content.put("image_url", image2Url);
        content.add(image2Content);
        
        // 发送请求
        ChatMessage message = new ChatMessage("user", content);
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gemini-2.0-flash",
            Arrays.asList(message)
        );
        
        ChatCompletionResponse response = client.createChatCompletion(request);
        System.out.println("Gemini比较结果: " + response.getContent());
    }
    
    /**
     * 图片问答示例
     */
    private static void imageQA(OpenAI client) throws Exception {
        // 创建第一轮对话：上传图片并提问
        List<Map<String, Object>> content = new ArrayList<>();
        
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", "这张图片中有什么动物吗？如果有，是什么动物？");
        content.add(textContent);
        
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, Object> imageUrl = new HashMap<>();
        // 使用一个包含动物的示例图片
        imageUrl.put("url", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/240px-Cat03.jpg");
        imageContent.put("image_url", imageUrl);
        content.add(imageContent);
        
        ChatMessage userMessage = new ChatMessage("user", content);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(userMessage);
        
        // 第一次提问
        ChatCompletionRequest request1 = new ChatCompletionRequest("gemini-2.0-flash", messages);
        ChatCompletionResponse response1 = client.createChatCompletion(request1);
        System.out.println("问: 这张图片中有什么动物吗？");
        System.out.println("答: " + response1.getContent());
        
        // 添加助手回复到对话历史
        messages.add(ChatMessage.assistant(response1.getContent()));
        
        // 第二次提问（基于同一张图片）
        messages.add(ChatMessage.user("这个动物通常生活在什么环境中？"));
        ChatCompletionRequest request2 = new ChatCompletionRequest("gemini-2.0-flash", messages);
        ChatCompletionResponse response2 = client.createChatCompletion(request2);
        System.out.println("\n问: 这个动物通常生活在什么环境中？");
        System.out.println("答: " + response2.getContent());
    }
}