package io.github.twwch.openai.sdk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 图片处理工具类
 */
public class ImageUtils {
    private static final Logger logger = LoggerFactory.getLogger(ImageUtils.class);
    
    // 简单的内存缓存，避免重复下载相同的图片
    private static final ConcurrentHashMap<String, CachedImage> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = TimeUnit.HOURS.toMillis(1); // 缓存1小时
    private static final int MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 压缩目标大小5MB
    private static final int MAX_DOWNLOAD_SIZE = Integer.parseInt(System.getProperty("openai.sdk.max.download.size", String.valueOf(50 * 1024 * 1024))); // 最大下载50MB，可通过系统属性配置
    private static final int CONNECT_TIMEOUT = 5000; // 连接超时5秒
    private static final int READ_TIMEOUT = 10000; // 读取超时10秒
    
    // 用于并发下载的线程池
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("ImageDownloader-" + thread.getId());
        return thread;
    });
    
    /**
     * 下载URL图片并转换为base64格式
     * 
     * @param imageUrl 图片URL
     * @return base64编码的图片数据，包含data URI前缀
     * @throws IOException 如果下载或转换失败
     */
    public static String downloadAndConvertToBase64(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new IllegalArgumentException("Image URL cannot be null or empty");
        }
        
        // Trim whitespace from URL
        imageUrl = imageUrl.trim();
        
        // 检查缓存
        CachedImage cached = IMAGE_CACHE.get(imageUrl);
        if (cached != null && !cached.isExpired()) {
            logger.debug("Using cached image for URL: {}", imageUrl);
            return cached.base64Data;
        }
        
        logger.debug("Downloading image from URL: {}", imageUrl);
        
        HttpURLConnection connection = null;
        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            // 设置User-Agent，避免被某些服务器拒绝
            connection.setRequestProperty("User-Agent", "OpenAI-SDK/1.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to download image. HTTP response code: " + responseCode);
            }
            
            // 获取Content-Type
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IOException("URL does not point to an image. Content-Type: " + contentType);
            }
            
            // 获取文件大小
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_DOWNLOAD_SIZE) {
                logger.error("Image size {} MB exceeds maximum allowed size {} MB for downloading", 
                    contentLength / (1024 * 1024), MAX_DOWNLOAD_SIZE / (1024 * 1024));
                throw new IOException(String.format("Image size %d MB exceeds maximum allowed size %d MB for downloading", 
                    contentLength / (1024 * 1024), MAX_DOWNLOAD_SIZE / (1024 * 1024)));
            }
            if (contentLength > MAX_IMAGE_SIZE) {
                logger.info("Image size {} MB exceeds target size {} MB, will download and compress", 
                    contentLength / (1024 * 1024), MAX_IMAGE_SIZE / (1024 * 1024));
            }
            
            // 读取图片数据 - 允许读取大文件以便后续压缩
            logger.debug("Starting to download image, expected size: {} bytes", contentLength);
            byte[] imageData = readInputStream(connection.getInputStream(), contentLength, MAX_DOWNLOAD_SIZE);
            
            logger.info("Successfully downloaded image, actual size: {} bytes", imageData.length);
            
            // 如果图片超过5MB，进行压缩
            if (imageData.length > MAX_IMAGE_SIZE) {
                logger.info("Image size {} MB exceeds target size {} MB, attempting compression...", 
                    imageData.length / (1024 * 1024), MAX_IMAGE_SIZE / (1024 * 1024));
                try {
                    byte[] compressedData = compressImage(imageData, MAX_IMAGE_SIZE - 100000); // 留100KB余量，确保base64编码后不超限
                    if (compressedData != null && compressedData.length > 0) {
                        if (compressedData.length < imageData.length) {
                            logger.info("Successfully compressed image from {} MB to {} MB", 
                                imageData.length / (1024 * 1024), compressedData.length / (1024 * 1024));
                            imageData = compressedData;
                        } else {
                            logger.warn("Compression did not reduce size, using original image");
                        }
                    } else {
                        logger.error("Compression returned null or empty data, using original image");
                    }
                } catch (Exception e) {
                    logger.error("Error during compression, using original image: {}", e.getMessage());
                    // 继续使用原始图片
                }
            }
            
            // 转换为base64
            String base64 = Base64.getEncoder().encodeToString(imageData);
            
            // 构建完整的data URI
            String mediaType = getMediaTypeFromContentType(contentType);
            String result = "data:" + mediaType + ";base64," + base64;
            
            // 缓存结果
            IMAGE_CACHE.put(imageUrl, new CachedImage(result));
            
            logger.debug("Successfully converted image to base64. Size: {} bytes", imageData.length);
            
            return result;
            
        }
        catch (IOException e){
            logger.error("IOException downloading image from URL: {}", imageUrl, e);
            throw e; // 重新抛出IOException
        }
        catch (Exception e){
            logger.error("Unexpected error downloading image from URL: {}", imageUrl, e);
            throw new IOException("Failed to download and convert image: " + e.getMessage(), e);
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * 从输入流读取数据
     * @param inputStream 输入流
     * @param expectedLength 预期长度（可能为-1）
     * @param maxSize 最大允许大小
     */
    private static byte[] readInputStream(InputStream inputStream, int expectedLength, int maxSize) throws IOException {
        // 如果知道预期大小，使用它来初始化缓冲区
        ByteArrayOutputStream buffer = expectedLength > 0 ? 
            new ByteArrayOutputStream(expectedLength) : new ByteArrayOutputStream();
        
        int nRead;
        byte[] data = new byte[16384]; // 增大缓冲区以提高读取效率
        int totalRead = 0;
        
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
            totalRead += nRead;
            
            // 每读取1MB打印一次进度日志
            if (totalRead % (1024 * 1024) == 0) {
                logger.debug("Downloaded {} MB so far...", totalRead / (1024 * 1024));
            }
            
            // 防止读取过大的文件
            if (totalRead > maxSize) {
                logger.error("Image size {} MB exceeds maximum allowed size {} MB", 
                    totalRead / (1024 * 1024), maxSize / (1024 * 1024));
                throw new IOException(String.format("Image size %d MB exceeds maximum allowed size %d MB for processing", 
                    totalRead / (1024 * 1024), maxSize / (1024 * 1024)));
            }
        }
        
        return buffer.toByteArray();
    }
    
    /**
     * 从Content-Type获取媒体类型
     */
    private static String getMediaTypeFromContentType(String contentType) {
        if (contentType == null) {
            return "image/jpeg"; // 默认值
        }
        
        // 移除可能的字符集等参数
        int semicolonIndex = contentType.indexOf(';');
        if (semicolonIndex != -1) {
            contentType = contentType.substring(0, semicolonIndex).trim();
        }
        
        return contentType;
    }
    
    /**
     * 清理过期的缓存
     */
    public static void clearExpiredCache() {
        IMAGE_CACHE.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 清理所有缓存
     */
    public static void clearAllCache() {
        IMAGE_CACHE.clear();
    }
    
    /**
     * 批量并发下载和转换图片
     * 
     * @param imageUrls 图片URL列表
     * @return URL到base64数据的映射，如果下载失败则值为null
     */
    public static Map<String, String> downloadAndConvertBatch(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return new ConcurrentHashMap<>();
        }
        
        // 去重
        List<String> uniqueUrls = imageUrls.stream()
            .distinct()
            .filter(url -> url != null && !url.isEmpty() && !url.startsWith("data:"))
            .collect(Collectors.toList());
        
        if (uniqueUrls.isEmpty()) {
            return new ConcurrentHashMap<>();
        }
        
        logger.debug("Starting batch download of {} images", uniqueUrls.size());
        
        ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (String url : uniqueUrls) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String trimmedUrl = url.trim(); // 确保URL没有空格
                    String base64Data = downloadAndConvertToBase64(trimmedUrl);
                    if (base64Data != null) {
                        // 使用原始URL作为key，以便调用者能找到结果
                        results.put(url, base64Data);
                        logger.debug("Successfully downloaded and converted image: {}", trimmedUrl);
                    }
                } catch (IOException e) {
                    // 详细记录IOException
                    if (e.getMessage() != null && e.getMessage().contains("exceeds maximum allowed size")) {
                        logger.error("Image too large to download: {} - {}", url, e.getMessage());
                    } else {
                        logger.error("IO error downloading image: {} - {}", url, e.getMessage());
                    }
                    // 不要在ConcurrentHashMap中存储null值
                    // 失败的URL将不会出现在结果中
                } catch (Exception e) {
                    logger.error("Unexpected error downloading image: {}", url, e);
                    // 失败的URL将不会出现在结果中
                }
            }, DOWNLOAD_EXECUTOR);
            
            futures.add(future);
        }
        
        // 等待所有下载完成，设置总超时时间
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            // 总超时时间 = 基础时间 + 每张图片额外时间
            long timeoutMs = 10000 + (uniqueUrls.size() * 2000L);
            allFutures.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.warn("Batch download timed out, some images may not have been downloaded");
            futures.forEach(f -> f.cancel(true));
        } catch (Exception e) {
            logger.error("Error during batch download", e);
        }
        
        logger.debug("Batch download completed. Successfully downloaded {} out of {} images", 
            results.values().stream().filter(v -> v != null).count(), uniqueUrls.size());
        
        return results;
    }
    
    /**
     * 关闭线程池（应在应用关闭时调用）
     */
    public static void shutdown() {
        DOWNLOAD_EXECUTOR.shutdown();
        try {
            if (!DOWNLOAD_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                DOWNLOAD_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            DOWNLOAD_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 下载图片并返回字节数组
     * 
     * @param imageUrl 图片URL
     * @return 图片的字节数组
     * @throws IOException 如果下载失败
     */
    public static byte[] downloadImage(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new IllegalArgumentException("Image URL cannot be null or empty");
        }
        
        // Trim whitespace from URL
        imageUrl = imageUrl.trim();
        
        logger.debug("Downloading image from URL: {}", imageUrl);
        
        HttpURLConnection connection = null;
        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", "OpenAI-SDK/1.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to download image. HTTP response code: " + responseCode);
            }
            
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IOException("URL does not point to an image. Content-Type: " + contentType);
            }
            
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_DOWNLOAD_SIZE) {
                throw new IOException(String.format("Image size %d MB exceeds maximum allowed size %d MB for downloading", 
                    contentLength / (1024 * 1024), MAX_DOWNLOAD_SIZE / (1024 * 1024)));
            }
            if (contentLength > MAX_IMAGE_SIZE) {
                logger.warn("Image size {} MB is large, will compress to {} MB", 
                    contentLength / (1024 * 1024), MAX_IMAGE_SIZE / (1024 * 1024));
            }
            
            byte[] imageData = readInputStream(connection.getInputStream(), contentLength, MAX_DOWNLOAD_SIZE);
            
            // 如果图片超过5MB，进行压缩
            if (imageData.length > MAX_IMAGE_SIZE) {
                int originalSize = imageData.length;
                imageData = compressImage(imageData, MAX_IMAGE_SIZE - 100000); // 留100KB余量
                logger.info("Image compressed from {} bytes to {} bytes", originalSize, imageData.length);
            }
            
            return imageData;
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * 检测图片的MIME类型
     * 
     * @param imageBytes 图片字节数组
     * @return MIME类型字符串
     */
    public static String detectMimeType(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length < 4) {
            return "image/jpeg"; // 默认值
        }
        
        // 检查文件签名（魔术数字）
        if (imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        } else if (imageBytes[0] == (byte) 0x89 && imageBytes[1] == 0x50 && 
                   imageBytes[2] == 0x4E && imageBytes[3] == 0x47) {
            return "image/png";
        } else if (imageBytes[0] == 0x47 && imageBytes[1] == 0x49 && 
                   imageBytes[2] == 0x46) {
            return "image/gif";
        } else if (imageBytes[0] == 0x42 && imageBytes[1] == 0x4D) {
            return "image/bmp";
        } else if ((imageBytes[0] == 0x49 && imageBytes[1] == 0x49 && 
                    imageBytes[2] == 0x2A && imageBytes[3] == 0x00) ||
                   (imageBytes[0] == 0x4D && imageBytes[1] == 0x4D && 
                    imageBytes[2] == 0x00 && imageBytes[3] == 0x2A)) {
            return "image/tiff";
        } else if (imageBytes.length > 10 && 
                   imageBytes[6] == 0x4A && imageBytes[7] == 0x46 && 
                   imageBytes[8] == 0x49 && imageBytes[9] == 0x46) {
            return "image/jpeg"; // JFIF format
        } else if (imageBytes.length > 12 &&
                   imageBytes[0] == 0x52 && imageBytes[1] == 0x49 &&
                   imageBytes[2] == 0x46 && imageBytes[3] == 0x46 &&
                   imageBytes[8] == 0x57 && imageBytes[9] == 0x45 &&
                   imageBytes[10] == 0x42 && imageBytes[11] == 0x50) {
            return "image/webp";
        }
        
        // 默认返回JPEG
        return "image/jpeg";
    }
    
    /**
     * 压缩图片到指定大小以下
     * 
     * @param imageBytes 原始图片字节数组
     * @param maxSizeBytes 最大字节数
     * @return 压缩后的图片字节数组
     */
    public static byte[] compressImage(byte[] imageBytes, int maxSizeBytes) {
        if (imageBytes == null || imageBytes.length <= maxSizeBytes) {
            return imageBytes;
        }
        
        logger.info("Starting image compression: {} MB -> target {} MB", 
            imageBytes.length / (1024 * 1024), maxSizeBytes / (1024 * 1024));
        
        try {
            // 检测图片格式
            String mimeType = detectMimeType(imageBytes);
            logger.debug("Detected image MIME type: {}", mimeType);
            
            // 读取原始图片
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage originalImage = ImageIO.read(bais);
            if (originalImage == null) {
                logger.error("ImageIO.read returned null for MIME type: {}. Image may be corrupted or unsupported format.", mimeType);
                // 尝试直接返回原始数据，让base64编码继续
                logger.warn("Returning original image without compression due to read failure");
                return imageBytes;
            }
            
            logger.debug("Successfully read image: {}x{} pixels", originalImage.getWidth(), originalImage.getHeight());
            
            // 检测图片格式
            String format = detectImageFormat(imageBytes);
            
            // 初始压缩质量
            float quality = 0.9f;
            byte[] compressedBytes = imageBytes;
            
            // 逐步降低质量直到满足大小要求
            int attemptCount = 0;
            while (compressedBytes.length > maxSizeBytes && attemptCount < 10) {
                attemptCount++;
                
                if (quality > 0.2f) {
                    // 先尝试降低质量
                    quality -= 0.1f;
                    compressedBytes = compressWithQuality(originalImage, format, quality);
                    logger.debug("Attempt {}: Compressed with quality {} - size: {} bytes", 
                        attemptCount, quality, compressedBytes.length);
                } else {
                    // 如果质量已经很低但仍然太大，需要缩放图片
                    double scale = Math.sqrt((double) maxSizeBytes / compressedBytes.length) * 0.9; // 留一点余量
                    if (scale < 0.1) {
                        scale = 0.1; // 最小缩放到10%
                    }
                    
                    int newWidth = Math.max(100, (int) (originalImage.getWidth() * scale));
                    int newHeight = Math.max(100, (int) (originalImage.getHeight() * scale));
                    
                    // 缩放图片
                    BufferedImage scaledImage = resizeImage(originalImage, newWidth, newHeight);
                    originalImage = scaledImage;
                    
                    // 重置质量并重新压缩
                    quality = 0.5f;
                    compressedBytes = compressWithQuality(originalImage, format, quality);
                    
                    logger.info("Resized image to {}x{} for compression, new size: {} bytes", 
                        newWidth, newHeight, compressedBytes.length);
                    
                    // 如果缩放后还是太大，继续降低质量
                    if (compressedBytes.length > maxSizeBytes) {
                        quality = 0.2f;
                        compressedBytes = compressWithQuality(originalImage, format, quality);
                    }
                }
            }
            
            logger.info("Image compressed from {} bytes to {} bytes (quality: {})", 
                imageBytes.length, compressedBytes.length, quality);
            
            return compressedBytes;
            
        } catch (IOException e) {
            logger.error("IOException during image compression: {}", e.getMessage(), e);
            // 对于IO异常，返回原始图片
            return imageBytes;
        } catch (Exception e) {
            logger.error("Unexpected error during image compression: {}", e.getMessage(), e);
            // 对于其他异常，也返回原始图片
            return imageBytes;
        }
    }
    
    /**
     * 使用指定质量压缩图片
     */
    private static byte[] compressWithQuality(BufferedImage image, String format, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // 对于JPEG格式，使用质量压缩
        if ("jpeg".equalsIgnoreCase(format) || "jpg".equalsIgnoreCase(format)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                writer.setOutput(ios);
                
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                
                // 如果图片有透明通道，需要转换为RGB
                BufferedImage rgbImage = image;
                if (image.getTransparency() != BufferedImage.OPAQUE) {
                    rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = rgbImage.createGraphics();
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, image.getWidth(), image.getHeight());
                    g.drawImage(image, 0, 0, null);
                    g.dispose();
                }
                
                writer.write(null, new IIOImage(rgbImage, null, null), param);
                ios.close();
                writer.dispose();
            } else {
                // 如果没有JPEG writer，使用默认方式
                ImageIO.write(image, format, baos);
            }
        } else if ("png".equalsIgnoreCase(format)) {
            // PNG是无损压缩，尝试转换为JPEG来减小大小
            logger.debug("Converting PNG to JPEG for compression");
            
            // 创建不带透明通道的图像
            BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgbImage.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
            
            // 使用JPEG压缩
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                writer.setOutput(ios);
                
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                
                writer.write(null, new IIOImage(rgbImage, null, null), param);
                ios.close();
                writer.dispose();
            } else {
                // 如果没有JPEG writer，使用默认方式
                ImageIO.write(rgbImage, "jpg", baos);
            }
        } else {
            // 对于其他格式，尝试写入
            logger.debug("Writing image in format: {}", format);
            if (!ImageIO.write(image, format, baos)) {
                // 如果写入失败，尝试转换为JPEG
                logger.warn("Failed to write in format {}, converting to JPEG", format);
                BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgbImage.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, image.getWidth(), image.getHeight());
                g.drawImage(image, 0, 0, null);
                g.dispose();
                ImageIO.write(rgbImage, "jpg", baos);
            }
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 缩放图片
     */
    private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, 
            originalImage.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : originalImage.getType());
        
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        
        return resizedImage;
    }
    
    /**
     * 检测图片格式
     */
    private static String detectImageFormat(byte[] imageBytes) {
        String mimeType = detectMimeType(imageBytes);
        
        // 从MIME类型提取格式
        if (mimeType.startsWith("image/")) {
            String format = mimeType.substring(6); // 移除 "image/" 前缀
            
            // 标准化格式名称
            if ("jpeg".equalsIgnoreCase(format)) {
                return "jpg";
            }
            return format.toLowerCase();
        }
        
        return "jpg"; // 默认JPEG
    }
    
    /**
     * 压缩Base64编码的图片
     * 
     * @param base64Data Base64编码的图片数据（包含或不包含data URI前缀）
     * @param maxSizeBytes 最大字节数
     * @return 压缩后的Base64编码图片数据（保持原有的data URI前缀格式）
     */
    public static String compressBase64Image(String base64Data, int maxSizeBytes) {
        if (base64Data == null || base64Data.isEmpty()) {
            return base64Data;
        }
        
        try {
            String base64Content;
            String prefix = "";
            
            // 解析data URI格式
            if (base64Data.startsWith("data:image/")) {
                String[] parts = base64Data.split(",", 2);
                if (parts.length == 2) {
                    prefix = parts[0] + ",";
                    base64Content = parts[1];
                } else {
                    return base64Data;
                }
            } else {
                base64Content = base64Data;
            }
            
            // 解码Base64
            byte[] imageBytes = Base64.getDecoder().decode(base64Content);
            
            // 检查是否需要压缩
            if (imageBytes.length <= maxSizeBytes) {
                return base64Data;
            }
            
            // 压缩图片（考虑base64编码后会增大约33%）
            int targetSize = (int)(maxSizeBytes * 0.75); // 留出base64编码的空间
            byte[] compressedBytes = compressImage(imageBytes, targetSize);
            
            // 重新编码为Base64
            String compressedBase64 = Base64.getEncoder().encodeToString(compressedBytes);
            
            // 如果原始数据有data URI前缀，保持相同格式（可能需要更新MIME类型）
            if (!prefix.isEmpty()) {
                // 检测压缩后的格式
                String mimeType = detectMimeType(compressedBytes);
                return "data:" + mimeType + ";base64," + compressedBase64;
            }
            
            return compressedBase64;
            
        } catch (Exception e) {
            logger.error("Failed to compress base64 image", e);
            return base64Data; // 返回原始数据
        }
    }
    
    /**
     * 缓存的图片数据
     */
    private static class CachedImage {
        final String base64Data;
        final long timestamp;
        
        CachedImage(String base64Data) {
            this.base64Data = base64Data;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }
}