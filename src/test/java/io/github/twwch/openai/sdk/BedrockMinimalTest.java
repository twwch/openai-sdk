package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.service.bedrock.ClaudeModelAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 测试Bedrock最小参数集
 */
public class BedrockMinimalTest {
    
    public static void main(String[] args) throws Exception {
        // 从环境变量获取凭证
        String bearerKey = System.getenv("AWS_BEARER_KEY_BEDROCK");
        String bearerToken = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
        
        if (bearerKey == null || bearerToken == null) {
            System.err.println("请设置环境变量 AWS_BEARER_KEY_BEDROCK 和 AWS_BEARER_TOKEN_BEDROCK");
            System.exit(1);
        }
        
        String modelId = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";
        
        // 测试1: 打印转换后的请求
        System.out.println("=== 测试1: 检查转换后的请求格式 ===");
        testRequestConversion();
        
        // 测试2: 最小参数流式请求
        System.out.println("\n=== 测试2: 最小参数流式请求 ===");
        testMinimalStreaming(bearerKey, bearerToken, modelId);
        
        // 测试3: 逐步添加参数
        System.out.println("\n=== 测试3: 逐步添加参数测试 ===");
        testIncrementalParameters(bearerKey, bearerToken, modelId);
    }
    
    // 测试请求转换
    private static void testRequestConversion() throws Exception {
        ClaudeModelAdapter adapter = new ClaudeModelAdapter();
        ObjectMapper mapper = new ObjectMapper();
        
        // 创建最小请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("claude-3");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("Count from 1 to 5 slowly"));
        request.setMessages(messages);
        
        // 清除所有OpenAI特有参数
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogprobs(null);
        request.setServiceTier(null);
        request.setParallelToolCalls(null);
        
        // 转换请求
        String bedrockRequest = adapter.convertRequest(request, mapper);
        System.out.println("转换后的Bedrock请求:");
        
        // 美化打印JSON
        JsonNode jsonNode = mapper.readTree(bedrockRequest);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));
    }
    
    // 测试最小参数流式请求
    private static void testMinimalStreaming(String bearerKey, String bearerToken, String modelId) throws Exception {
        OpenAI client = OpenAI.bedrock("us-east-2", bearerKey, bearerToken, modelId);
        
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("Say hello"));
        request.setMessages(messages);
        
        request.setStream(true);
        
        // 完全清除所有可能的参数
        request.setMaxTokens(null); // 让适配器使用默认值
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setStop(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogitBias(null);
        request.setUser(null);
        request.setFunctions(null);
        request.setFunctionCall(null);
        request.setTools(null);
        request.setToolChoice(null);
        request.setResponseFormat(null);
        request.setStreamOptions(null); // 甚至不设置stream_options
        request.setAudio(null);
        request.setLogprobs(null);
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
        request.setTopLogprobs(null);
        request.setWebSearchOptions(null);
        
        executeStreamRequest(client, request, "最小参数");
    }
    
    // 逐步添加参数测试
    private static void testIncrementalParameters(String bearerKey, String bearerToken, String modelId) throws Exception {
        OpenAI client = OpenAI.bedrock("us-east-2", bearerKey, bearerToken, modelId);
        
        // 基础消息
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("Count from 1 to 3"));
        
        // 测试1: 只有必需参数
        System.out.println("\n--- 只有必需参数 ---");
        ChatCompletionRequest request1 = new ChatCompletionRequest();
        request1.setModel(modelId);
        request1.setMessages(messages);
        request1.setStream(true);
        clearAllOptionalParams(request1);
        executeStreamRequest(client, request1, "必需参数");
        
        // 测试2: 添加temperature
        System.out.println("\n--- 添加temperature ---");
        ChatCompletionRequest request2 = new ChatCompletionRequest();
        request2.setModel(modelId);
        request2.setMessages(messages);
        request2.setStream(true);
        request2.setTemperature(0.7);
        clearAllOptionalParams(request2);
        request2.setTemperature(0.7); // 重新设置
        executeStreamRequest(client, request2, "temperature=0.7");
        
        // 测试3: 添加max_tokens
        System.out.println("\n--- 添加max_tokens ---");
        ChatCompletionRequest request3 = new ChatCompletionRequest();
        request3.setModel(modelId);
        request3.setMessages(messages);
        request3.setStream(true);
        request3.setMaxTokens(100);
        clearAllOptionalParams(request3);
        request3.setMaxTokens(100); // 重新设置
        executeStreamRequest(client, request3, "max_tokens=100");
        
        // 测试4: 添加stream_options
        System.out.println("\n--- 添加stream_options ---");
        ChatCompletionRequest request4 = new ChatCompletionRequest();
        request4.setModel(modelId);
        request4.setMessages(messages);
        request4.setStream(true);
        request4.setStreamOptions(new ChatCompletionRequest.StreamOptions(true));
        clearAllOptionalParams(request4);
        request4.setStreamOptions(new ChatCompletionRequest.StreamOptions(true)); // 重新设置
        executeStreamRequest(client, request4, "stream_options");
    }
    
    // 清除所有可选参数
    private static void clearAllOptionalParams(ChatCompletionRequest request) {
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setStop(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogitBias(null);
        request.setUser(null);
        request.setFunctions(null);
        request.setFunctionCall(null);
        request.setTools(null);
        request.setToolChoice(null);
        request.setResponseFormat(null);
        request.setAudio(null);
        request.setLogprobs(null);
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
        request.setTopLogprobs(null);
        request.setWebSearchOptions(null);
    }
    
    // 执行流式请求
    private static void executeStreamRequest(OpenAI client, ChatCompletionRequest request, String testName) {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder content = new StringBuilder();
        AtomicReference<String> error = new AtomicReference<>();
        
        try {
            client.createChatCompletionStream(
                request,
                chunk -> {
                    String chunkContent = chunk.getContent();
                    if (chunkContent != null && !chunkContent.isEmpty()) {
                        content.append(chunkContent);
                    }
                },
                () -> {
                    System.out.println(testName + " - 成功: " + content.toString());
                    latch.countDown();
                },
                err -> {
                    error.set(err.getMessage());
                    System.err.println(testName + " - 失败: " + err.getMessage());
                    latch.countDown();
                }
            );
            
            latch.await();
            
        } catch (Exception e) {
            System.err.println(testName + " - 异常: " + e.getMessage());
        }
    }
}