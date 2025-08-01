package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * 调试Bedrock METRIC_VALUES错误
 */
public class BedrockDebugDemo {
    
    public static void main(String[] args) throws Exception {
        // 从环境变量获取凭证
        String bearerKey = System.getenv("AWS_BEARER_KEY_BEDROCK");
        String bearerToken = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
        
        if (bearerKey == null || bearerToken == null) {
            System.err.println("请设置环境变量 AWS_BEARER_KEY_BEDROCK 和 AWS_BEARER_TOKEN_BEDROCK");
            System.exit(1);
        }
        
        String modelId = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";
        OpenAI client = OpenAI.bedrock("us-east-2", bearerKey, bearerToken, modelId);
        
        // 测试1: 简单请求（无工具）
        System.out.println("=== 测试1: 简单流式请求（无工具）===");
        testSimpleStreaming(client, modelId);
        
        // 测试2: 少量工具
        System.out.println("\n=== 测试2: 流式请求（少量工具）===");
        testStreamingWithFewTools(client, modelId);
        
        // 测试3: 大量工具
        System.out.println("\n=== 测试3: 流式请求（大量工具）===");
        testStreamingWithManyTools(client, modelId);
        
        // 测试4: 清理参数后的请求
        System.out.println("\n=== 测试4: 清理参数后的流式请求 ===");
        testCleanedRequest(client, modelId);
    }
    
    // 测试1: 简单流式请求
    private static void testSimpleStreaming(OpenAI client, String modelId) throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("你好"));
        
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(messages);
        request.setStream(true);
        
        // 设置stream_options
        ChatCompletionRequest.StreamOptions streamOptions = new ChatCompletionRequest.StreamOptions(true);
        request.setStreamOptions(streamOptions);
        
        // 清除所有默认值
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogprobs(null);
        request.setServiceTier(null);
        
        executeStreamRequest(client, request, "简单请求");
    }
    
    // 测试2: 少量工具
    private static void testStreamingWithFewTools(OpenAI client, String modelId) throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("你好"));
        
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(messages);
        request.setStream(true);
        
        // 添加少量工具（3个）
        List<ChatCompletionRequest.Tool> tools = createFewTools();
        request.setTools(tools);
        request.setToolChoice("auto");
        
        // 设置stream_options
        ChatCompletionRequest.StreamOptions streamOptions = new ChatCompletionRequest.StreamOptions(true);
        request.setStreamOptions(streamOptions);
        
        // 清除默认值
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogprobs(null);
        request.setServiceTier(null);
        request.setParallelToolCalls(null);
        
        executeStreamRequest(client, request, "少量工具");
    }
    
    // 测试3: 大量工具
    private static void testStreamingWithManyTools(OpenAI client, String modelId) throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("你好"));
        
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(messages);
        request.setStream(true);
        
        // 添加大量工具（30个）
        List<ChatCompletionRequest.Tool> tools = createManyTools();
        request.setTools(tools);
        request.setToolChoice("auto");
        
        // 设置stream_options
        ChatCompletionRequest.StreamOptions streamOptions = new ChatCompletionRequest.StreamOptions(true);
        request.setStreamOptions(streamOptions);
        
        // 保留默认值（模拟原始请求）
        
        executeStreamRequest(client, request, "大量工具");
    }
    
    // 测试4: 清理后的请求
    private static void testCleanedRequest(OpenAI client, String modelId) throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 使用原始的系统消息
        messages.add(ChatMessage.system("You are a helpful assistant."));
        messages.add(ChatMessage.user("你好"));
        
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(messages);
        request.setStream(true);
        request.setMaxTokens(1000); // 设置明确的max_tokens
        
        // 只添加少量工具
        List<ChatCompletionRequest.Tool> tools = createFewTools();
        request.setTools(tools);
        request.setToolChoice("auto");
        
        // 设置stream_options
        ChatCompletionRequest.StreamOptions streamOptions = new ChatCompletionRequest.StreamOptions(true);
        request.setStreamOptions(streamOptions);
        
        // 完全清除所有可能导致问题的参数
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogprobs(null);
        request.setServiceTier(null);
        request.setParallelToolCalls(null);
        request.setUser(null);
        request.setResponseFormat(null);
        request.setSeed(null);
        request.setTopLogprobs(null);
        request.setStore(null);
        
        // 打印实际请求内容
        ObjectMapper mapper = new ObjectMapper();
        System.out.println("实际请求参数:");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(request));
        
        executeStreamRequest(client, request, "清理参数");
    }
    
    // 执行流式请求
    private static void executeStreamRequest(OpenAI client, ChatCompletionRequest request, String testName) {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder content = new StringBuilder();
        final boolean[] success = {false};
        final String[] error = {null};
        
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
                    success[0] = true;
                    System.out.println(testName + " - 成功: " + content.toString());
                    latch.countDown();
                },
                err -> {
                    error[0] = err.getMessage();
                    System.err.println(testName + " - 失败: " + err.getMessage());
                    err.printStackTrace();
                    latch.countDown();
                }
            );
            
            latch.await();
            
        } catch (Exception e) {
            System.err.println(testName + " - 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 创建少量工具
    private static List<ChatCompletionRequest.Tool> createFewTools() {
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        
        // 工具1: 获取天气
        ChatCompletionRequest.Tool weatherTool = new ChatCompletionRequest.Tool();
        weatherTool.setType("function");
        ChatCompletionRequest.Function weatherFunc = new ChatCompletionRequest.Function();
        weatherFunc.setName("get_weather");
        weatherFunc.setDescription("Get weather information");
        Map<String, Object> weatherParams = new HashMap<>();
        weatherParams.put("type", "object");
        weatherParams.put("properties", Map.of(
            "location", Map.of("type", "string", "description", "City name")
        ));
        weatherParams.put("required", Arrays.asList("location"));
        weatherFunc.setParameters(weatherParams);
        weatherTool.setFunction(weatherFunc);
        tools.add(weatherTool);
        
        // 工具2: 搜索知识库
        ChatCompletionRequest.Tool searchTool = new ChatCompletionRequest.Tool();
        searchTool.setType("function");
        ChatCompletionRequest.Function searchFunc = new ChatCompletionRequest.Function();
        searchFunc.setName("search_knowledge");
        searchFunc.setDescription("Search the knowledge base");
        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("type", "object");
        searchParams.put("properties", Map.of(
            "query", Map.of("type", "string", "description", "Search query")
        ));
        searchParams.put("required", Arrays.asList("query"));
        searchFunc.setParameters(searchParams);
        searchTool.setFunction(searchFunc);
        tools.add(searchTool);
        
        return tools;
    }
    
    // 创建大量工具（模拟原始请求）
    private static List<ChatCompletionRequest.Tool> createManyTools() {
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        
        // 创建30个工具
        String[] toolNames = {
            "video-summarizer", "get_current_weather", "ytb_transcript",
            "ai-flowchart-generator", "mp4-to-text", "tiktok-transcript-generator",
            "podcast-summarizer", "parser_url", "read_file_full_content",
            "ai-homework-helper", "search_knowledge", "true-or-false-generator",
            "ai-story-generator", "ai-chart-maker", "terms-of-service-analyzer",
            "ai-quiz-generator", "ai-website-summarizer", "audio-to-text-converter",
            "ai-notes-generator", "youtube-transcript-generator", "ai-question-generator",
            "relationship-chart-maker", "ai-document-summarizer", "ai-study-guide-maker",
            "ai-audio-summarizer", "ai-report-writer", "job-description-generator",
            "ai-argument-generator", "free-ai-text-humanizer", "ai-youtube-name-generator"
        };
        
        for (String toolName : toolNames) {
            ChatCompletionRequest.Tool tool = new ChatCompletionRequest.Tool();
            tool.setType("function");
            ChatCompletionRequest.Function func = new ChatCompletionRequest.Function();
            func.setName(toolName);
            func.setDescription("Tool for " + toolName);
            Map<String, Object> params = new HashMap<>();
            params.put("type", "object");
            params.put("properties", new HashMap<>());
            params.put("required", new ArrayList<>());
            func.setParameters(params);
            tool.setFunction(func);
            tools.add(tool);
        }
        
        return tools;
    }
}