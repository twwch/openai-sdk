package io.github.twwch.openai.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试Bedrock和OpenAI的Usage字段映射
 */
public class UsageFieldMappingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testBedrockUsageMapping() throws Exception {
        // Bedrock格式的JSON (使用下划线命名)
        String bedrockJson = "{"
            + "\"input_tokens\": 2205,"
            + "\"output_tokens\": 150,"
            + "\"cache_creation_input_tokens\": 6672,"
            + "\"cache_read_input_tokens\": 0"
            + "}";

        ChatCompletionResponse.Usage usage = mapper.readValue(bedrockJson, ChatCompletionResponse.Usage.class);

        // 验证字段映射
        assertEquals(2205, usage.getPromptTokens(), "input_tokens应该映射到promptTokens");
        assertEquals(150, usage.getCompletionTokens(), "output_tokens应该映射到completionTokens");
        assertEquals(6672, usage.getCacheCreationInputTokens(), "cache_creation_input_tokens应该正确映射");
        assertEquals(0, usage.getCacheReadInputTokens(), "cache_read_input_tokens应该正确映射");

        System.out.println("✅ Bedrock字段映射测试通过");
        System.out.println("  input_tokens -> promptTokens: " + usage.getPromptTokens());
        System.out.println("  output_tokens -> completionTokens: " + usage.getCompletionTokens());
        System.out.println("  cache_creation_input_tokens: " + usage.getCacheCreationInputTokens());
        System.out.println("  cache_read_input_tokens: " + usage.getCacheReadInputTokens());
    }

    @Test
    public void testOpenAIUsageMapping() throws Exception {
        // OpenAI格式的JSON (使用下划线命名，但字段名不同)
        String openAIJson = "{"
            + "\"prompt_tokens\": 100,"
            + "\"completion_tokens\": 50,"
            + "\"total_tokens\": 150"
            + "}";

        ChatCompletionResponse.Usage usage = mapper.readValue(openAIJson, ChatCompletionResponse.Usage.class);

        // 验证字段映射
        assertEquals(100, usage.getPromptTokens(), "prompt_tokens应该映射到promptTokens");
        assertEquals(50, usage.getCompletionTokens(), "completion_tokens应该映射到completionTokens");
        assertEquals(150, usage.getTotalTokens(), "total_tokens应该正确映射");

        System.out.println("✅ OpenAI字段映射测试通过");
        System.out.println("  prompt_tokens -> promptTokens: " + usage.getPromptTokens());
        System.out.println("  completion_tokens -> completionTokens: " + usage.getCompletionTokens());
        System.out.println("  total_tokens: " + usage.getTotalTokens());
    }

    @Test
    public void testBedrockCacheHit() throws Exception {
        // Bedrock缓存命中的JSON
        String bedrockCacheHitJson = "{"
            + "\"input_tokens\": 17,"
            + "\"output_tokens\": 200,"
            + "\"cache_creation_input_tokens\": 0,"
            + "\"cache_read_input_tokens\": 1056"
            + "}";

        ChatCompletionResponse.Usage usage = mapper.readValue(bedrockCacheHitJson, ChatCompletionResponse.Usage.class);

        // 验证字段映射
        assertEquals(17, usage.getPromptTokens());
        assertEquals(200, usage.getCompletionTokens());
        assertEquals(0, usage.getCacheCreationInputTokens());
        assertEquals(1056, usage.getCacheReadInputTokens());

        // 验证缓存命中检测
        assertNotNull(usage.getCacheReadInputTokens(), "应该有缓存读取tokens");
        assertTrue(usage.getCacheReadInputTokens() > 0, "缓存读取tokens应该大于0");

        System.out.println("✅ Bedrock缓存命中测试通过");
        System.out.println("  新处理的input_tokens: " + usage.getPromptTokens());
        System.out.println("  从缓存读取的tokens: " + usage.getCacheReadInputTokens());
        System.out.println("  💰 节省约90%成本!");
    }

    @Test
    public void testBedrockCacheCreation() throws Exception {
        // Bedrock创建缓存的JSON
        String bedrockCacheCreationJson = "{"
            + "\"input_tokens\": 16,"
            + "\"output_tokens\": 200,"
            + "\"cache_creation_input_tokens\": 1056,"
            + "\"cache_read_input_tokens\": 0"
            + "}";

        ChatCompletionResponse.Usage usage = mapper.readValue(bedrockCacheCreationJson, ChatCompletionResponse.Usage.class);

        // 验证字段映射
        assertEquals(16, usage.getPromptTokens());
        assertEquals(200, usage.getCompletionTokens());
        assertEquals(1056, usage.getCacheCreationInputTokens());
        assertEquals(0, usage.getCacheReadInputTokens());

        // 验证缓存创建检测
        assertNotNull(usage.getCacheCreationInputTokens(), "应该有缓存创建tokens");
        assertTrue(usage.getCacheCreationInputTokens() > 0, "缓存创建tokens应该大于0");

        System.out.println("✅ Bedrock缓存创建测试通过");
        System.out.println("  新处理的input_tokens: " + usage.getPromptTokens());
        System.out.println("  创建缓存的tokens: " + usage.getCacheCreationInputTokens());
        System.out.println("  ⚠️  创建缓存成本约为标准输入的125%");
    }

    @Test
    public void testAzureUsageMapping() throws Exception {
        // Azure格式的JSON (带有嵌套的prompt_tokens_details)
        String azureJson = "{"
            + "\"prompt_tokens\": 1500,"
            + "\"completion_tokens\": 150,"
            + "\"total_tokens\": 1650,"
            + "\"prompt_tokens_details\": {"
            + "  \"cached_tokens\": 1200"
            + "}"
            + "}";

        ChatCompletionResponse.Usage usage = mapper.readValue(azureJson, ChatCompletionResponse.Usage.class);

        // 验证字段映射
        assertEquals(1500, usage.getPromptTokens());
        assertEquals(150, usage.getCompletionTokens());
        assertEquals(1650, usage.getTotalTokens());

        // 验证Azure缓存字段
        assertNotNull(usage.getPromptTokensDetails(), "应该有prompt_tokens_details");
        assertEquals(1200, usage.getPromptTokensDetails().getCachedTokens(), "cached_tokens应该正确映射");

        // 验证getCacheReadInputTokens()统一接口
        assertEquals(1200, usage.getCacheReadInputTokens(), "getCacheReadInputTokens应该返回Azure的cached_tokens");

        System.out.println("✅ Azure字段映射测试通过");
        System.out.println("  prompt_tokens: " + usage.getPromptTokens());
        System.out.println("  completion_tokens: " + usage.getCompletionTokens());
        System.out.println("  cached_tokens (通过prompt_tokens_details): " + usage.getPromptTokensDetails().getCachedTokens());
        System.out.println("  cached_tokens (通过统一接口): " + usage.getCacheReadInputTokens());
    }
}
