package io.github.twwch.openai.sdk.service.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Bedrock请求验证器
 * 用于验证和清理请求参数，避免METRIC_VALUES错误
 */
public class BedrockRequestValidator {
    private static final Logger logger = LoggerFactory.getLogger(BedrockRequestValidator.class);
    
    private static final Set<String> SUPPORTED_PARAMS = new HashSet<>();
    
    static {
        // Bedrock Claude支持的参数
        SUPPORTED_PARAMS.add("anthropic_version");
        SUPPORTED_PARAMS.add("max_tokens");
        SUPPORTED_PARAMS.add("messages");
        SUPPORTED_PARAMS.add("system");
        SUPPORTED_PARAMS.add("temperature");
        SUPPORTED_PARAMS.add("top_p");
        SUPPORTED_PARAMS.add("top_k");
        SUPPORTED_PARAMS.add("stop_sequences");
        SUPPORTED_PARAMS.add("tools");
        SUPPORTED_PARAMS.add("tool_choice");
        SUPPORTED_PARAMS.add("anthropic_beta");
    }
    
    /**
     * 验证并清理请求
     */
    public static void validateAndCleanRequest(ChatCompletionRequest request) {
        // 清除所有不支持的OpenAI参数
        request.setN(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogprobs(null);
        request.setTopLogprobs(null);
        request.setLogitBias(null);
        request.setUser(null);
        request.setResponseFormat(null);
        request.setAudio(null);
        request.setMaxCompletionTokens(null);
        request.setMetadata(null);
        request.setModalities(null);
        request.setParallelToolCalls(null);
        request.setPrediction(null);
        request.setPromptCacheKey(null);
        request.setReasoningEffort(null);
        request.setSafetyIdentifier(null);
        request.setSeed(null);
        request.setServiceTier(null);
        request.setStore(null);
        request.setWebSearchOptions(null);
        
        // 验证参数范围
        if (request.getTemperature() != null) {
            double temp = request.getTemperature();
            if (temp < 0 || temp > 1) {
                request.setTemperature(null);
            }
        }
        
        if (request.getTopP() != null) {
            double topP = request.getTopP();
            if (topP < 0 || topP > 1) {
                request.setTopP(null);
            }
        }
    }
    
    /**
     * 验证转换后的Bedrock请求
     */
    public static boolean validateBedrockRequest(String bedrockRequest, ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(bedrockRequest);
            
            // 检查是否包含不支持的参数
            root.fieldNames().forEachRemaining(fieldName -> {
                if (!SUPPORTED_PARAMS.contains(fieldName)) {
                    logger.warn("请求包含不支持的参数: {}", fieldName);
                }
            });
            
            // 检查必需参数
            if (!root.has("anthropic_version")) {
                logger.error("缺少必需参数 anthropic_version");
                return false;
            }
            
            if (!root.has("max_tokens")) {
                logger.error("缺少必需参数 max_tokens");
                return false;
            }
            
            if (!root.has("messages")) {
                logger.error("缺少必需参数 messages");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("验证请求失败", e);
            return false;
        }
    }
}