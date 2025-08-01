package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.concurrent.CountDownLatch;

public class BedrockToolCallDemoFixed {
    
    public static void main(String[] args) throws Exception {
        // 从环境变量获取Bearer凭证
        String bearerKey = System.getenv("AWS_BEARER_KEY_BEDROCK");
        String bearerToken = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
        
        if (bearerKey == null || bearerToken == null) {
            System.err.println("请设置环境变量 AWS_BEARER_KEY_BEDROCK 和 AWS_BEARER_TOKEN_BEDROCK");
            System.exit(1);
        }
        
        // 创建Bedrock客户端
        String modelId = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";
        OpenAI client = OpenAI.bedrock("us-east-2", bearerKey, bearerToken, modelId);
        
        System.out.println("=== Bedrock 工具调用示例 (修复版) ===");
        System.out.println("模型: " + modelId);
        System.out.println();
        
        // 定义工具
        List<ChatCompletionRequest.Tool> tools = createTools();
        
        // 测试1: 在系统提示中要求使用工具格式（不通过API提供工具）
        System.out.println("=== 测试1: 仅通过系统提示要求工具调用 ===");
        List<ChatMessage> messages1 = new ArrayList<>();
//        messages1.add(ChatMessage.system("你是一个有帮助的助手。当用户询问天气时，使用get_weather工具获取天气信息。请使用以下格式调用工具：\n<tool_call>\n{\"name\": \"工具名\", \"args\": {参数}}\n</tool_call>"));
        messages1.add(ChatMessage.user("北京和上海今天的天气怎么样？"));
        
        // 第一次调用 - 让模型决定是否使用工具
        System.out.println("用户: 北京和上海今天的天气怎么样？");
        System.out.println("\n1. 发送请求给模型...");
        
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(messages1);
        request.setTools(tools);
        request.setToolChoice("auto");
        
        // 清除不必要的默认值
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogprobs(null);
        request.setServiceTier(null);
        
        ChatCompletionResponse response = client.createChatCompletion(request);
        ChatMessage assistantMessage = response.getChoices().get(0).getMessage();
        messages1.add(assistantMessage);
        
        System.out.println("\n模型响应:");
        String content = assistantMessage.getContent();
        System.out.println("内容: " + content);
        
        // 检查API返回的工具调用
        if (assistantMessage.getToolCalls() != null && assistantMessage.getToolCalls().length > 0) {
            System.out.println("\n2. 检测到工具调用:");
            
            for (ChatMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                System.out.println("  - ID: " + toolCall.getId());
                System.out.println("    工具: " + toolCall.getFunction().getName());
                System.out.println("    参数: " + toolCall.getFunction().getArguments());
                
                // 执行工具调用
                String toolResult = executeToolCall(toolCall);
                System.out.println("    结果: " + toolResult);
                
                // 将工具结果添加为tool消息
                messages1.add(ChatMessage.tool(toolCall.getId(), toolResult));
            }
            
            // 第二次调用 - 让模型基于工具结果生成最终回复
            System.out.println("\n3. 将工具结果发送给模型...");
            
            // 继续提供工具，让模型可以继续调用
            request = new ChatCompletionRequest();
            request.setModel(modelId);
            request.setMessages(messages1);
            request.setTools(tools); // 继续提供工具
            request.setToolChoice("auto");
            
            // 清除不必要的默认值
            request.setTemperature(null);
            request.setTopP(null);
            request.setN(null);
            request.setPresencePenalty(null);
            request.setFrequencyPenalty(null);
            request.setLogprobs(null);
            request.setServiceTier(null);
            
            ChatCompletionResponse finalResponse = client.createChatCompletion(request);
            ChatMessage finalMessage = finalResponse.getChoices().get(0).getMessage();
            
            System.out.println("\n最终回复:");
            System.out.println("内容: " + finalMessage.getContent());
            
            // 检查是否有更多工具调用
            if (finalMessage.getToolCalls() != null && finalMessage.getToolCalls().length > 0) {
                System.out.println("检测到更多工具调用:");
                for (ChatMessage.ToolCall tc : finalMessage.getToolCalls()) {
                    System.out.println("  - " + tc.getFunction().getName() + ": " + tc.getFunction().getArguments());
                }
            }
        }
        
        // 测试2: 通过API提供工具
        System.out.println("\n\n=== 测试2: 通过API提供工具 ===");
        testWithAPITools(client, modelId, tools);
        
        // 测试3: 流式工具调用
        System.out.println("\n\n=== 测试3: 流式工具调用测试 ===");
        testStreamingToolCall(client, modelId);
    }
    
    
    // 创建工具定义
    private static List<ChatCompletionRequest.Tool> createTools() {
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        
        // 创建天气查询工具
        ChatCompletionRequest.Tool weatherTool = new ChatCompletionRequest.Tool();
        weatherTool.setType("function");
        
        ChatCompletionRequest.Function weatherFunction = new ChatCompletionRequest.Function();
        weatherFunction.setName("get_weather");
        weatherFunction.setDescription("获取指定城市的天气信息");
        
        // 定义参数schema
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> location = new HashMap<>();
        location.put("type", "string");
        location.put("description", "城市名称，例如：北京、上海");
        properties.put("location", location);
        
        Map<String, Object> unit = new HashMap<>();
        unit.put("type", "string");
        unit.put("enum", Arrays.asList("celsius", "fahrenheit"));
        unit.put("description", "温度单位");
        properties.put("unit", unit);
        
        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("location"));
        
        weatherFunction.setParameters(parameters);
        weatherTool.setFunction(weatherFunction);
        
        tools.add(weatherTool);
        
        return tools;
    }
    
    // 执行工具调用
    private static String executeToolCall(ChatMessage.ToolCall toolCall) {
        String toolName = toolCall.getFunction().getName();
        System.out.println("执行工具调用: " + toolName);
        
        if ("get_weather".equals(toolName)) {
            try {
                // 解析参数
                ObjectMapper mapper = new ObjectMapper();
                JsonNode args = mapper.readTree(toolCall.getFunction().getArguments());
                
                String location = args.has("location") ? args.get("location").asText() : "";
                String unit = args.has("unit") ? args.get("unit").asText() : "celsius";
                
                // 模拟天气数据
                if ("北京".equals(location)) {
                    return String.format("北京天气：晴天，温度 18°%s，湿度 45%%", 
                        "celsius".equals(unit) ? "C" : "F");
                } else if ("上海".equals(location)) {
                    return String.format("上海天气：多云，温度 22°%s，湿度 65%%", 
                        "celsius".equals(unit) ? "C" : "F");
                } else {
                    return location + "天气：未知";
                }
            } catch (Exception e) {
                return "解析参数失败: " + e.getMessage();
            }
        }
        
        return "未知工具：" + toolName;
    }
    
    // 测试通过API提供工具
    private static void testWithAPITools(OpenAI client, String modelId, List<ChatCompletionRequest.Tool> tools) throws Exception {
        System.out.println("测试通过API tools参数提供工具...");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("北京今天的天气怎么样？"));
        
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(messages);
        request.setTools(tools);
        request.setToolChoice("auto");
        
        // 清除默认值
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogprobs(null);
        request.setServiceTier(null);
        
        System.out.println("\n发送带tools参数的请求...");
        ChatCompletionResponse response = client.createChatCompletion(request);
        String content = response.getContent();
        
        System.out.println("\n模型响应:");
        System.out.println(content);
        
        // 检查响应中的tool_calls字段
        ChatMessage message = response.getChoices().get(0).getMessage();
        if (message.getToolCalls() != null && message.getToolCalls().length > 0) {
            System.out.println("\n通过API返回的工具调用:");
            for (ChatMessage.ToolCall toolCall : message.getToolCalls()) {
                System.out.println("  - ID: " + toolCall.getId());
                System.out.println("    工具: " + toolCall.getFunction().getName());
                System.out.println("    参数: " + toolCall.getFunction().getArguments());
            }
        } else {
            System.out.println("\nAPI响应中没有tool_calls字段");
        }
    }
    
    // 测试流式工具调用
    private static void testStreamingToolCall(OpenAI client, String modelId) throws Exception {
        System.out.println("测试流式响应中的工具调用和usage信息...");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("计算 123 + 456 的结果"));
        
        // 创建计算工具
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        ChatCompletionRequest.Tool calcTool = new ChatCompletionRequest.Tool();
        calcTool.setType("function");
        
        ChatCompletionRequest.Function calcFunction = new ChatCompletionRequest.Function();
        calcFunction.setName("calculate");
        calcFunction.setDescription("执行数学计算");
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> expression = new HashMap<>();
        expression.put("type", "string");
        expression.put("description", "要计算的数学表达式");
        properties.put("expression", expression);
        
        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("expression"));
        
        calcFunction.setParameters(parameters);
        calcTool.setFunction(calcFunction);
        tools.add(calcTool);
        
        // 创建流式请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(messages);
        request.setTools(tools);
        request.setStream(true);
        
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
        
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder contentBuilder = new StringBuilder();
        final ChatCompletionResponse.Usage[] finalUsage = {null};
        
        System.out.println("\n流式响应:");
        
        client.createChatCompletionStream(
            request,
            chunk -> {
                // 收集内容
                String content = chunk.getContent();
                if (content != null && !content.isEmpty()) {
                    System.out.print(content);
                    contentBuilder.append(content);
                }
                
                // 收集usage信息 - message_delta事件包含累积的usage
                if (chunk.getUsage() != null) {
                    finalUsage[0] = chunk.getUsage();
                }
                
                // 检查是否是最后一个chunk
                if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                    String finishReason = chunk.getChoices().get(0).getFinishReason();
                    if ("stop".equals(finishReason) && chunk.getUsage() == null && finalUsage[0] == null) {
                        // 如果是最后一个chunk但没有usage，可能需要从其他地方获取
                        System.out.println("\n[注意] 最后一个chunk没有包含usage信息");
                    }
                }
            },
            () -> {
                System.out.println("\n\n流式响应完成");
                
                // 流式响应中的工具调用需要特别处理
                // Bedrock的流式响应可能不会返回工具调用
                String fullContent = contentBuilder.toString();
                System.out.println("\n完整响应内容: " + fullContent);
                
                // 显示usage信息
                if (finalUsage[0] != null) {
                    System.out.println("\nUsage信息:");
                    System.out.println("  输入tokens: " + finalUsage[0].getPromptTokens());
                    System.out.println("  输出tokens: " + finalUsage[0].getCompletionTokens());
                    System.out.println("  总tokens: " + finalUsage[0].getTotalTokens());
                } else {
                    System.out.println("\n未获取到Usage信息");
                }
                
                latch.countDown();
            },
            error -> {
                System.err.println("错误: " + error.getMessage());
                error.printStackTrace();
                latch.countDown();
            }
        );
        
        latch.await();
    }
}