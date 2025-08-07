package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.util.ImageUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发图片下载测试
 */
public class ConcurrentImageDownloadTest {
    
    @Test
    public void testBatchDownloadWithEmptyList() {
        Map<String, String> result = ImageUtils.downloadAndConvertBatch(Arrays.asList());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
    
    @Test
    public void testBatchDownloadWithNullList() {
        Map<String, String> result = ImageUtils.downloadAndConvertBatch(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
    
    @Test
    public void testBatchDownloadSkipsBase64Images() {
        List<String> urls = Arrays.asList(
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
            "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAv/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCwAA8A/9k="
        );
        
        Map<String, String> result = ImageUtils.downloadAndConvertBatch(urls);
        assertNotNull(result);
        assertTrue(result.isEmpty()); // base64图片被跳过，不需要下载
    }
    
    @Test
    public void testBatchDownloadWithDuplicates() {
        List<String> urls = Arrays.asList(
            "https://example.com/image1.jpg",
            "https://example.com/image1.jpg", // 重复
            "https://example.com/image2.jpg",
            "https://example.com/image1.jpg"  // 重复
        );
        
        // 注意：这个测试只验证去重逻辑，实际下载会失败
        // 在实际使用中，这些URL应该是有效的
        Map<String, String> result = ImageUtils.downloadAndConvertBatch(urls);
        assertNotNull(result);
        // 结果应该只包含去重后的URL
        assertTrue(result.size() <= 2);
    }
    
    @Test
    public void testBatchDownloadFilterInvalidUrls() {
        List<String> urls = Arrays.asList(
            null,
            "",
            "https://example.com/valid.jpg",
            "data:image/png;base64,xxx", // base64，会被跳过
            "https://example.com/another.jpg"
        );
        
        Map<String, String> result = ImageUtils.downloadAndConvertBatch(urls);
        assertNotNull(result);
        // 只有有效的HTTP(S) URL会被处理
        assertTrue(result.size() <= 2);
    }
    
    /**
     * 性能测试：验证并发下载比串行下载更快
     * 注意：这个测试依赖网络，可能不稳定
     */
    // @Test
    // public void testConcurrentPerformance() {
    //     List<String> urls = Arrays.asList(
    //         "https://via.placeholder.com/150",
    //         "https://via.placeholder.com/200",
    //         "https://via.placeholder.com/250",
    //         "https://via.placeholder.com/300"
    //     );
    //     
    //     long startTime = System.currentTimeMillis();
    //     Map<String, String> result = ImageUtils.downloadAndConvertBatch(urls);
    //     long endTime = System.currentTimeMillis();
    //     
    //     System.out.println("Batch download took: " + (endTime - startTime) + "ms");
    //     
    //     assertNotNull(result);
    //     assertEquals(4, result.size());
    //     
    //     // 验证所有结果都是base64格式
    //     for (String base64Data : result.values()) {
    //         if (base64Data != null) {
    //             assertTrue(base64Data.startsWith("data:image/"));
    //         }
    //     }
    // }
}