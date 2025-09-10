package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.service.bedrock.BedrockImageProcessor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Bedrock图片处理测试
 * 测试超大图片的自动缩放功能
 */
public class BedrockImageTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Bedrock 图片处理测试 ===\n");
        
        // 测试1：测试图片尺寸检测
        testImageDimensionDetection();
        
        // 测试2：测试图片缩放
        testImageResizing();
        
        // 测试3：测试实际Bedrock请求（需要配置凭证）
        if (args.length > 0 && "run-bedrock".equals(args[0])) {
            testBedrockWithImage();
        }
        
        System.out.println("\n=== 测试完成 ===");
    }
    
    /**
     * 测试图片尺寸检测
     */
    private static void testImageDimensionDetection() throws Exception {
        System.out.println("测试1：图片尺寸检测");
        System.out.println("------------------------");
        
        // 创建一个测试图片（10000x5000）
        BufferedImage testImage = createTestImage(10000, 5000, Color.BLUE);
        String base64Image = imageToBase64(testImage, "png");
        String dataUrl = "data:image/png;base64," + base64Image;
        
        // 检测尺寸
        BedrockImageProcessor.ImageDimension dimension = BedrockImageProcessor.getImageDimension(dataUrl);
        System.out.println("原始图片尺寸: " + dimension);
        System.out.println("是否超出限制: " + dimension.isOversized());
        
        // 检查是否需要缩放
        boolean needsResize = BedrockImageProcessor.isImageOversized(dataUrl);
        System.out.println("需要缩放: " + needsResize);
        
        System.out.println("✓ 尺寸检测测试通过\n");
    }
    
    /**
     * 测试图片缩放
     */
    private static void testImageResizing() throws Exception {
        System.out.println("测试2：图片自动缩放");
        System.out.println("------------------------");
        
        // 测试案例1：宽度超限（10000x5000 -> 8000x4000）
        System.out.println("\n案例1: 宽度超限");
        BufferedImage wideImage = createTestImage(10000, 5000, Color.RED);
        testResize(wideImage, "png");
        
        // 测试案例2：高度超限（5000x10000 -> 4000x8000）
        System.out.println("\n案例2: 高度超限");
        BufferedImage tallImage = createTestImage(5000, 10000, Color.GREEN);
        testResize(tallImage, "png");
        
        // 测试案例3：两维都超限（12000x9000 -> 8000x6000）
        System.out.println("\n案例3: 两维都超限");
        BufferedImage largeImage = createTestImage(12000, 9000, Color.YELLOW);
        testResize(largeImage, "png");
        
        // 测试案例4：不需要缩放（6000x4000）
        System.out.println("\n案例4: 不需要缩放");
        BufferedImage normalImage = createTestImage(6000, 4000, Color.CYAN);
        testResize(normalImage, "png");
        
        System.out.println("✓ 缩放测试通过\n");
    }
    
    /**
     * 测试单个图片的缩放
     */
    private static void testResize(BufferedImage image, String format) throws Exception {
        String base64Image = imageToBase64(image, format);
        String dataUrl = "data:image/" + format + ";base64," + base64Image;
        
        // 获取原始尺寸
        BedrockImageProcessor.ImageDimension originalDim = 
            BedrockImageProcessor.getImageDimension(dataUrl);
        System.out.println("  原始尺寸: " + originalDim);
        
        // 处理图片
        String processedImage = BedrockImageProcessor.processImage(dataUrl, "image/" + format);
        
        // 获取处理后尺寸
        BedrockImageProcessor.ImageDimension processedDim = 
            BedrockImageProcessor.getImageDimension(processedImage);
        System.out.println("  处理后尺寸: " + processedDim);
        
        // 验证缩放是否正确
        if (originalDim.isOversized()) {
            assert !processedDim.isOversized() : "缩放后仍然超出限制";
            assert processedDim.getWidth() <= 8000 : "宽度仍然超出限制";
            assert processedDim.getHeight() <= 8000 : "高度仍然超出限制";
            System.out.println("  ✓ 成功缩放到限制内");
        } else {
            assert originalDim.getWidth() == processedDim.getWidth() : "不应该改变宽度";
            assert originalDim.getHeight() == processedDim.getHeight() : "不应该改变高度";
            System.out.println("  ✓ 保持原始尺寸");
        }
    }
    
    /**
     * 测试实际的Bedrock请求（需要配置AWS凭证）
     */
    private static void testBedrockWithImage() throws Exception {
        System.out.println("测试3：Bedrock图片请求");
        System.out.println("------------------------");
        
        // 从环境变量获取配置
        String region = System.getProperty("bedrock.region", "us-west-2");
        String accessKeyId = System.getProperty("bedrock.accessKeyId", System.getenv("AWS_ACCESS_KEY_ID"));
        String secretAccessKey = System.getProperty("bedrock.secretAccessKey", System.getenv("AWS_SECRET_ACCESS_KEY"));
        String modelId = System.getProperty("bedrock.modelId", "anthropic.claude-3-sonnet-20240229");
        
        if (accessKeyId == null || secretAccessKey == null) {
            System.err.println("跳过Bedrock测试：未配置AWS凭证");
            return;
        }
        
        // 创建Bedrock服务
        OpenAI service = OpenAI.bedrock(region, accessKeyId, secretAccessKey, modelId);
        
        // 创建一个超大的测试图片（模拟用户上传的大图）
        BufferedImage largeImage = createTestImage(10000, 6000, Color.BLUE);
        String base64Image = imageToBase64(largeImage, "png");
        String dataUrl = "data:image/png;base64," + base64Image;
        
        System.out.println("发送包含超大图片的请求...");
        System.out.println("原始图片尺寸: 10000x6000");
        
        // 构建包含图片的请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMaxTokens(100);
        
        List<ChatMessage> messages = new ArrayList<>();
        
        // 创建包含图片的消息
        ChatMessage.ContentPart[] content = new ChatMessage.ContentPart[] {
            ChatMessage.ContentPart.text("请描述这个图片（这是一个测试图片，纯色背景）"),
            ChatMessage.ContentPart.imageUrl(dataUrl)
        };
        
        messages.add(ChatMessage.user(content));
        request.setMessages(messages);
        
        try {
            // 发送请求（会自动缩放图片）
            ChatCompletionResponse response = service.createChatCompletion(request);
            
            System.out.println("✓ 请求成功！");
            System.out.println("响应: " + response.getChoices().get(0).getMessage().getContentAsString());
            
        } catch (Exception e) {
            System.err.println("请求失败: " + e.getMessage());
            if (e.getMessage().contains("8000")) {
                System.err.println("❌ 图片缩放可能未生效");
            }
        }
    }
    
    /**
     * 创建测试图片
     */
    private static BufferedImage createTestImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // 填充背景色
        g2d.setColor(color);
        g2d.fillRect(0, 0, width, height);
        
        // 添加文字标记
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, Math.min(width, height) / 20));
        String text = width + "x" + height;
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();
        g2d.drawString(text, (width - textWidth) / 2, (height + textHeight) / 2);
        
        g2d.dispose();
        return image;
    }
    
    /**
     * 将图片转换为base64
     */
    private static String imageToBase64(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}