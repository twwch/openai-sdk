package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.util.ImageUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图片工具类测试
 */
public class ImageUtilsTest {
    
    @Test
    public void testBase64ImageParsing() {
        // 测试base64图片解析
        String base64Image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
        
        // 验证格式
        assertTrue(base64Image.startsWith("data:image/"));
        
        String[] parts = base64Image.split(",", 2);
        assertEquals(2, parts.length);
        
        String mediaType = parts[0].substring(5, parts[0].indexOf(";"));
        assertEquals("image/png", mediaType);
        
        assertNotNull(parts[1]); // base64数据部分
    }
    
    @Test 
    public void testInvalidUrl() {
        // 测试无效URL
        assertThrows(IllegalArgumentException.class, () -> {
            ImageUtils.downloadAndConvertToBase64(null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            ImageUtils.downloadAndConvertToBase64("");
        });
    }
    
    @Test
    public void testCacheFunctionality() {
        // 测试缓存清理功能
        ImageUtils.clearAllCache();
        
        // 这里只测试方法可以正常调用，不做实际的下载测试
        ImageUtils.clearExpiredCache();
    }
    
    /**
     * 注意：这个测试需要网络连接，且依赖外部图片URL
     * 在CI环境中可能需要跳过
     */
    // @Test
    // public void testRealImageDownload() throws Exception {
    //     // 使用一个稳定的测试图片URL
    //     String testImageUrl = "https://via.placeholder.com/150";
    //     String result = ImageUtils.downloadAndConvertToBase64(testImageUrl);
    //     
    //     assertNotNull(result);
    //     assertTrue(result.startsWith("data:image/"));
    //     assertTrue(result.contains(";base64,"));
    // }
}