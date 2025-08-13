package io.github.twwch.openai.sdk;

/**
 * Google Gemini API配置类
 * 用于配置Google Gemini服务的访问参数
 */
public class GeminiConfig extends OpenAIConfig {
    // https://ai.google.dev/gemini-api/docs/openai?hl=zh-cn
    public static final String DEFAULT_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai";
    
    /**
     * 创建Gemini配置
     * @param apiKey Google AI Studio API密钥
     */
    public GeminiConfig(String apiKey) {
        this(apiKey, DEFAULT_GEMINI_BASE_URL);
    }
    
    /**
     * 创建自定义基础URL的Gemini配置
     * @param apiKey Google AI Studio API密钥
     * @param baseUrl 自定义API基础URL
     */
    public GeminiConfig(String apiKey, String baseUrl) {
        super(apiKey, baseUrl, 600, null);
    }
    
    /**
     * 创建完整自定义配置
     * @param apiKey Google AI Studio API密钥
     * @param baseUrl API基础URL
     * @param timeout 超时时间（秒）
     */
    public GeminiConfig(String apiKey, String baseUrl, int timeout) {
        super(apiKey, baseUrl, timeout, null);
    }
    
    /**
     * 是否为Gemini配置
     * @return 总是返回true
     */
    public boolean isGemini() {
        return true;
    }
    
    @Override
    public boolean isAzure() {
        return false;
    }
    
    @Override
    public boolean isBedrock() {
        return false;
    }
}