package io.github.twwch.openai.sdk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
    private static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 最大10MB
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
            if (contentLength > MAX_IMAGE_SIZE) {
                throw new IOException("Image size exceeds maximum allowed size of " + MAX_IMAGE_SIZE + " bytes");
            }
            
            // 读取图片数据
            byte[] imageData = readInputStream(connection.getInputStream(), contentLength);
            
            // 转换为base64
            String base64 = Base64.getEncoder().encodeToString(imageData);
            
            // 构建完整的data URI
            String mediaType = getMediaTypeFromContentType(contentType);
            String result = "data:" + mediaType + ";base64," + base64;
            
            // 缓存结果
            IMAGE_CACHE.put(imageUrl, new CachedImage(result));
            
            logger.debug("Successfully converted image to base64. Size: {} bytes", imageData.length);
            
            return result;
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * 从输入流读取数据
     */
    private static byte[] readInputStream(InputStream inputStream, int expectedLength) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        
        int nRead;
        byte[] data = new byte[8192];
        int totalRead = 0;
        
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
            totalRead += nRead;
            
            // 防止读取过大的文件
            if (totalRead > MAX_IMAGE_SIZE) {
                throw new IOException("Image size exceeds maximum allowed size");
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
                    String base64Data = downloadAndConvertToBase64(url);
                    results.put(url, base64Data);
                } catch (Exception e) {
                    logger.error("Failed to download image: {}", url, e);
                    // 不要在ConcurrentHashMap中存储null值
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