package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.Arrays;

/**
 * AWS Bedrock Claude 图片分析示例
 * 演示如何使用Claude 3模型来分析图片
 * 
 * 注意：
 * 1. 需要使用Claude 3 Sonnet或Claude 3 Opus模型，它们支持图片分析
 * 2. URL图片会自动下载并转换为base64格式（因为Bedrock不直接支持URL）
 * 3. 支持并发下载多张图片，提高性能
 */
public class BedrockImageExample {
    public static void main(String[] args) {
        // 从环境变量获取AWS凭证
        String accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
        String secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        String region = System.getenv("AWS_REGION");
        
        if (region == null) {
            region = "us-east-2"; // 默认区域
        }
        
        // 使用Claude 3 Sonnet模型（支持图片）
        String modelId = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";
        
        // 创建Bedrock客户端
        OpenAI bedrockClient = OpenAI.bedrock(region, accessKeyId, secretAccessKey, modelId);
        
        // 示例1：分析单张URL图片
        testSingleUrlImage(bedrockClient, modelId);
        
        // 示例2：分析Base64编码的图片
//        testBase64Image(bedrockClient, modelId);
//
//        // 示例3：并发分析多张图片
//        testMultipleImagesConcurrent(bedrockClient, modelId);
//
//        // 示例4：图片与文本混合分析
//        testMixedContent(bedrockClient, modelId);
    }
    
    /**
     * 示例1：分析单张URL图片
     * SDK会自动下载URL图片并转换为base64
     */
    private static void testSingleUrlImage(OpenAI client, String modelId) {
        System.out.println("\n=== 示例1：分析URL图片 ===");
        
        ChatMessage.ContentPart[] parts = new ChatMessage.ContentPart[] {
            ChatMessage.ContentPart.text("请详细描述这张自然风景图片，包括天气、时间、景色特点等。"),
            ChatMessage.ContentPart.imageUrl("https://cdn-aws.iweaver.ai/docx/2025/08/28/9033ff93-7ef4-42e7-a507-3584e0280cc7/哲风壁纸_天空草地-少年奔跑.png")
        };
        
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(parts);
        
        try {
            long startTime = System.currentTimeMillis();
            
            ChatCompletionResponse response = client.createChatCompletion(
                modelId,
                Arrays.asList(message)
            );
            
            long endTime = System.currentTimeMillis();
            
            System.out.println("Claude分析结果: " + response.getContent());
            System.out.println("处理时间: " + (endTime - startTime) + "ms");
            System.out.println("使用的tokens: " + response.getUsage().getTotalTokens());
            
        } catch (Exception e) {
            System.err.println("分析失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 示例2：分析Base64编码的图片
     * Base64图片直接传递，无需转换
     */
    private static void testBase64Image(OpenAI client, String modelId) {
        System.out.println("\n=== 示例2：分析Base64图片 ===");
        
        // 一个简单的红色1x1像素PNG图片
        String base64RedPixel = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
        
        ChatMessage.ContentPart[] parts = new ChatMessage.ContentPart[] {
            ChatMessage.ContentPart.text("这是一个1x1像素的图片。请告诉我这个像素是什么颜色？"),
            ChatMessage.ContentPart.imageUrl(base64RedPixel)
        };
        
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(parts);
        
        try {
            ChatCompletionResponse response = client.createChatCompletion(
                modelId,
                Arrays.asList(
                    ChatMessage.system("你是一个精确的图像分析助手。"),
                    message
                )
            );
            
            System.out.println("颜色分析: " + response.getContent());
            
        } catch (Exception e) {
            System.err.println("分析失败: " + e.getMessage());
        }
    }
    
    /**
     * 示例3：并发分析多张图片
     * 展示并发下载的性能优势
     */
    private static void testMultipleImagesConcurrent(OpenAI client, String modelId) {
        System.out.println("\n=== 示例3：并发分析多张图片 ===");
        
        // 多张图片URL - SDK会并发下载这些图片
        ChatMessage.ContentPart[] parts = new ChatMessage.ContentPart[] {
            ChatMessage.ContentPart.text("请比较以下三张动物图片："),
            ChatMessage.ContentPart.text("\n图片1 - 猫："),
            ChatMessage.ContentPart.imageUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/320px-Cat03.jpg"),
            ChatMessage.ContentPart.text("\n图片2 - 狗："),
            ChatMessage.ContentPart.imageUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/4/42/Beagle1.jpg/320px-Beagle1.jpg"),
            ChatMessage.ContentPart.text("\n图片3 - 鸟："),
            ChatMessage.ContentPart.imageUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/4/45/Eopsaltria_australis_-_Mogo_Campground.jpg/320px-Eopsaltria_australis_-_Mogo_Campground.jpg"),
            ChatMessage.ContentPart.text("\n请描述每种动物的特征，并说明它们作为宠物的优缺点。")
        };
        
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(parts);
        
        try {
            long startTime = System.currentTimeMillis();
            
            ChatCompletionResponse response = client.createChatCompletion(
                modelId,
                Arrays.asList(
                    ChatMessage.system("你是一个动物专家，请提供专业的分析。"),
                    message
                )
            );
            
            long endTime = System.currentTimeMillis();
            
            System.out.println("动物比较分析: " + response.getContent());
            System.out.println("处理时间（包括并发下载）: " + (endTime - startTime) + "ms");
            
        } catch (Exception e) {
            System.err.println("分析失败: " + e.getMessage());
        }
    }
    
    /**
     * 示例4：图片与文本混合分析
     * 展示复杂的多模态交互
     */
    private static void testMixedContent(OpenAI client, String modelId) {
        System.out.println("\n=== 示例4：图片与文本混合分析 ===");
        
        // 第一轮：发送图片让AI识别
        ChatMessage.ContentPart[] parts1 = new ChatMessage.ContentPart[] {
            ChatMessage.ContentPart.text("请看这张图片："),
            ChatMessage.ContentPart.imageUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png/320px-Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png"),
            ChatMessage.ContentPart.text("这是什么动物？请描述它的特征。")
        };
        
        ChatMessage userMessage1 = new ChatMessage();
        userMessage1.setRole("user");
        userMessage1.setContent(parts1);
        
        try {
            // 第一轮对话
            ChatCompletionResponse response1 = client.createChatCompletion(
                modelId,
                Arrays.asList(userMessage1)
            );
            
            System.out.println("AI识别结果: " + response1.getContent());
            
            // 第二轮：基于识别结果继续对话
            ChatMessage assistantMessage = ChatMessage.assistant(response1.getContent());
            ChatMessage userMessage2 = ChatMessage.user("这种动物通常生活在什么环境中？它们的寿命大概是多少？");
            
            ChatCompletionResponse response2 = client.createChatCompletion(
                modelId,
                Arrays.asList(
                    userMessage1,
                    assistantMessage,
                    userMessage2
                )
            );
            
            System.out.println("\n详细信息: " + response2.getContent());
            
            // 第三轮：发送另一张图片进行比较
            ChatMessage.ContentPart[] parts3 = new ChatMessage.ContentPart[] {
                ChatMessage.ContentPart.text("现在看看这张图片："),
                ChatMessage.ContentPart.imageUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/320px-Cat03.jpg"),
                ChatMessage.ContentPart.text("这和刚才的动物有什么区别？")
            };
            
            ChatMessage userMessage3 = new ChatMessage();
            userMessage3.setRole("user");
            userMessage3.setContent(parts3);
            
            ChatCompletionResponse response3 = client.createChatCompletion(
                modelId,
                Arrays.asList(
                    userMessage1,
                    assistantMessage,
                    userMessage2,
                    ChatMessage.assistant(response2.getContent()),
                    userMessage3
                )
            );
            
            System.out.println("\n比较分析: " + response3.getContent());
            
        } catch (Exception e) {
            System.err.println("对话失败: " + e.getMessage());
        }
    }
}