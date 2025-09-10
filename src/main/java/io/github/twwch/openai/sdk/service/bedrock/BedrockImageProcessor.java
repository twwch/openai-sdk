package io.github.twwch.openai.sdk.service.bedrock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Bedrock图片处理器
 * 处理图片尺寸限制，自动缩放超过8000px的图片
 */
public class BedrockImageProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BedrockImageProcessor.class);
    
    // Bedrock的最大图片尺寸限制
    private static final int MAX_IMAGE_SIZE = 8000;
    
    // 图片质量设置
    private static final float JPEG_QUALITY = 0.9f;
    
    /**
     * 处理base64编码的图片
     * 如果图片尺寸超过8000px，自动缩放
     * 
     * @param base64Image base64编码的图片数据（可能包含data:image/png;base64,前缀）
     * @param mediaType 媒体类型（如image/jpeg, image/png）
     * @return 处理后的base64图片数据
     */
    public static String processImage(String base64Image, String mediaType) {
        if (base64Image == null || base64Image.isEmpty()) {
            return base64Image;
        }
        
        try {
            // 提取纯base64数据（去除data URL前缀）
            String base64Data = extractBase64Data(base64Image);
            
            // 解码base64为字节数组
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            
            // 读取图片
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (originalImage == null) {
                logger.warn("无法读取图片数据");
                return base64Image;
            }
            
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            logger.debug("原始图片尺寸: {}x{}", originalWidth, originalHeight);
            
            // 检查是否需要缩放
            if (originalWidth <= MAX_IMAGE_SIZE && originalHeight <= MAX_IMAGE_SIZE) {
                logger.debug("图片尺寸符合要求，无需缩放");
                return base64Image;
            }
            
            // 计算缩放比例
            double scale = calculateScale(originalWidth, originalHeight);
            int newWidth = (int) (originalWidth * scale);
            int newHeight = (int) (originalHeight * scale);
            
            logger.info("图片尺寸超出限制 ({}x{})，缩放至 {}x{}", 
                originalWidth, originalHeight, newWidth, newHeight);
            
            // 缩放图片
            BufferedImage scaledImage = resizeImage(originalImage, newWidth, newHeight);
            
            // 将缩放后的图片转回base64
            String format = getImageFormat(mediaType);
            byte[] scaledImageBytes = imageToBytes(scaledImage, format);
            String scaledBase64 = Base64.getEncoder().encodeToString(scaledImageBytes);
            
            // 如果原始数据有data URL前缀，需要恢复
            if (base64Image.startsWith("data:")) {
                String prefix = base64Image.substring(0, base64Image.indexOf(",") + 1);
                return prefix + scaledBase64;
            }
            
            return scaledBase64;
            
        } catch (Exception e) {
            logger.error("处理图片失败", e);
            // 如果处理失败，返回原始图片
            return base64Image;
        }
    }
    
    /**
     * 提取纯base64数据（去除data URL前缀）
     */
    private static String extractBase64Data(String base64Image) {
        if (base64Image.startsWith("data:")) {
            // 格式: data:image/png;base64,iVBORw0KGgo...
            int commaIndex = base64Image.indexOf(",");
            if (commaIndex > 0) {
                return base64Image.substring(commaIndex + 1);
            }
        }
        return base64Image;
    }
    
    /**
     * 计算缩放比例
     */
    private static double calculateScale(int width, int height) {
        double scaleX = width > MAX_IMAGE_SIZE ? (double) MAX_IMAGE_SIZE / width : 1.0;
        double scaleY = height > MAX_IMAGE_SIZE ? (double) MAX_IMAGE_SIZE / height : 1.0;
        // 使用较小的缩放比例，确保两个维度都不超过限制
        return Math.min(scaleX, scaleY);
    }
    
    /**
     * 缩放图片
     * 使用高质量的缩放算法
     */
    private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        // 创建目标图片
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, 
            originalImage.getType() != 0 ? originalImage.getType() : BufferedImage.TYPE_INT_ARGB);
        
        // 使用Graphics2D进行高质量缩放
        Graphics2D g2d = resizedImage.createGraphics();
        try {
            // 设置渲染提示以获得最佳质量
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // 绘制缩放后的图片
            g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2d.dispose();
        }
        
        return resizedImage;
    }
    
    /**
     * 将图片转换为字节数组
     */
    private static byte[] imageToBytes(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // 如果是JPEG格式，可以设置压缩质量
        if ("jpeg".equalsIgnoreCase(format) || "jpg".equalsIgnoreCase(format)) {
            // 对于JPEG，使用ImageIO的默认压缩
            ImageIO.write(image, format, baos);
        } else {
            // 对于其他格式（如PNG），直接写入
            ImageIO.write(image, format, baos);
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 根据媒体类型获取图片格式
     */
    private static String getImageFormat(String mediaType) {
        if (mediaType == null) {
            return "png"; // 默认格式
        }
        
        mediaType = mediaType.toLowerCase();
        if (mediaType.contains("jpeg") || mediaType.contains("jpg")) {
            return "jpeg";
        } else if (mediaType.contains("png")) {
            return "png";
        } else if (mediaType.contains("gif")) {
            return "gif";
        } else if (mediaType.contains("webp")) {
            return "webp";
        } else {
            return "png"; // 默认格式
        }
    }
    
    /**
     * 检查图片尺寸是否超出限制
     * 
     * @param base64Image base64编码的图片
     * @return 如果超出限制返回true
     */
    public static boolean isImageOversized(String base64Image) {
        try {
            String base64Data = extractBase64Data(base64Image);
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            
            if (image == null) {
                return false;
            }
            
            return image.getWidth() > MAX_IMAGE_SIZE || image.getHeight() > MAX_IMAGE_SIZE;
            
        } catch (Exception e) {
            logger.error("检查图片尺寸失败", e);
            return false;
        }
    }
    
    /**
     * 获取图片尺寸信息
     * 
     * @param base64Image base64编码的图片
     * @return 图片尺寸信息
     */
    public static ImageDimension getImageDimension(String base64Image) {
        try {
            String base64Data = extractBase64Data(base64Image);
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            
            if (image == null) {
                return null;
            }
            
            return new ImageDimension(image.getWidth(), image.getHeight());
            
        } catch (Exception e) {
            logger.error("获取图片尺寸失败", e);
            return null;
        }
    }
    
    /**
     * 图片尺寸信息
     */
    public static class ImageDimension {
        private final int width;
        private final int height;
        
        public ImageDimension(int width, int height) {
            this.width = width;
            this.height = height;
        }
        
        public int getWidth() {
            return width;
        }
        
        public int getHeight() {
            return height;
        }
        
        public boolean isOversized() {
            return width > MAX_IMAGE_SIZE || height > MAX_IMAGE_SIZE;
        }
        
        @Override
        public String toString() {
            return width + "x" + height;
        }
    }
}