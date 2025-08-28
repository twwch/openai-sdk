package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.util.ImageUtils;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图片压缩功能测试
 */
public class ImageCompressionTest {
    
    @Test
    public void testImageCompression() throws IOException {
        // 创建一个大图片（模拟超过5MB的图片）
        BufferedImage largeImage = createLargeImage();
        byte[] largeImageBytes = imageToBytes(largeImage, "jpg");
        
        System.out.println("Original image size: " + largeImageBytes.length + " bytes");
        
        // 压缩图片
        int maxSize = 5 * 1024 * 1024; // 5MB
        byte[] compressedBytes = ImageUtils.compressImage(largeImageBytes, maxSize);
        
        System.out.println("Compressed image size: " + compressedBytes.length + " bytes");
        
        // 验证压缩后的大小
        assertTrue(compressedBytes.length <= maxSize, 
            "Compressed image should be less than or equal to " + maxSize + " bytes");
        
        // 验证压缩后的图片仍然有效
        BufferedImage compressedImage = ImageIO.read(new java.io.ByteArrayInputStream(compressedBytes));
        assertNotNull(compressedImage, "Compressed image should be valid");
    }
    
    @Test
    public void testBase64ImageCompression() throws IOException {
        // 创建一个大图片
        BufferedImage largeImage = createLargeImage();
        byte[] largeImageBytes = imageToBytes(largeImage, "jpg");
        
        // 转换为Base64
        String base64Image = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(largeImageBytes);
        System.out.println("Original base64 length: " + base64Image.length());
        
        // 压缩Base64图片
        int maxSize = 5 * 1024 * 1024; // 5MB
        String compressedBase64 = ImageUtils.compressBase64Image(base64Image, maxSize);
        
        System.out.println("Compressed base64 length: " + compressedBase64.length());
        
        // 验证压缩后的Base64仍然有效
        assertTrue(compressedBase64.startsWith("data:image/"), "Should maintain data URI format");
        
        // 解码并验证大小
        String[] parts = compressedBase64.split(",", 2);
        byte[] decodedBytes = Base64.getDecoder().decode(parts[1]);
        assertTrue(decodedBytes.length <= maxSize, "Decoded image should be within size limit");
    }
    
    @Test
    public void testSmallImageNotCompressed() {
        // 创建一个小图片（小于5MB）
        byte[] smallImageBytes = new byte[1024 * 1024]; // 1MB
        
        // 尝试压缩
        int maxSize = 5 * 1024 * 1024; // 5MB
        byte[] result = ImageUtils.compressImage(smallImageBytes, maxSize);
        
        // 验证没有被压缩（返回原始数组）
        assertSame(smallImageBytes, result, "Small image should not be compressed");
    }
    
    /**
     * 创建一个大的测试图片
     */
    private BufferedImage createLargeImage() {
        // 创建一个超高分辨率图片，确保生成的JPEG文件超过5MB
        int width = 8000;
        int height = 6000;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g = image.createGraphics();
        
        // 填充复杂的内容以增加文件大小
        // 使用随机像素填充，这会导致JPEG压缩效果变差，文件变大
        for (int x = 0; x < width; x += 10) {
            for (int y = 0; y < height; y += 10) {
                g.setColor(new Color((int)(Math.random() * 255), 
                                     (int)(Math.random() * 255), 
                                     (int)(Math.random() * 255)));
                g.fillRect(x, y, 10, 10);
            }
        }
        
        // 添加更多随机元素
        for (int i = 0; i < 10000; i++) {
            g.setColor(new Color((int)(Math.random() * 255), 
                                 (int)(Math.random() * 255), 
                                 (int)(Math.random() * 255)));
            int x = (int)(Math.random() * width);
            int y = (int)(Math.random() * height);
            int w = (int)(Math.random() * 200) + 50;
            int h = (int)(Math.random() * 200) + 50;
            if (Math.random() > 0.5) {
                g.fillOval(x, y, w, h);
            } else {
                g.fillRect(x, y, w, h);
            }
        }
        
        // 添加一些文本
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 120));
        g.drawString("Large Test Image for Compression", 100, 200);
        
        g.dispose();
        
        return image;
    }
    
    /**
     * 将BufferedImage转换为字节数组
     */
    private byte[] imageToBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
}