package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.concurrent.CountDownLatch;

public class BedrockToolCallDemo {
    
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
        
        System.out.println("=== Bedrock 工具调用示例 (使用Bearer认证) ===");
        System.out.println("模型: " + modelId);
        System.out.println();
        
        // 定义工具
        List<ChatCompletionRequest.Tool> tools = createTools();
        
        // 创建初始对话
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("你是一个有帮助的助手。当用户询问天气时，使用get_weather工具获取天气信息。"));
        messages.add(ChatMessage.user("北京和上海今天的天气怎么样？"));
        
        // 第一次调用 - 让模型决定是否使用工具
        System.out.println("用户: 北京和上海今天的天气怎么样？");
        System.out.println("\n1. 发送请求给模型...");
        
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(messages);
        request.setTools(tools);
        request.setToolChoice("auto"); // 让模型自动决定是否使用工具
        
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
        messages.add(assistantMessage);
        
        System.out.println("\n模型响应:");
        if (assistantMessage.getContent() != null) {
            System.out.println("内容: " + assistantMessage.getContent());
        }
        
        // 检查是否有工具调用
        if (assistantMessage.getToolCalls() != null && assistantMessage.getToolCalls().length > 0) {
            System.out.println("\n2. 模型请求调用以下工具:");
            
            for (ChatMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                System.out.println("  - 工具: " + toolCall.getFunction().getName());
                System.out.println("    参数: " + toolCall.getFunction().getArguments());
                
                // 模拟工具调用结果
                String toolResult = executeToolCall(toolCall);
                
                // 将工具调用结果添加到对话中
                ChatMessage toolMessage = ChatMessage.tool(toolCall.getId(), toolResult);
                messages.add(toolMessage);
                
                System.out.println("    结果: " + toolResult);
            }
            
            // 第二次调用 - 让模型基于工具结果生成最终回复
            System.out.println("\n3. 将工具结果发送给模型...");
            
            request.setMessages(messages);
            request.setTools(null); // 第二次调用不需要工具
            
            ChatCompletionResponse finalResponse = client.createChatCompletion(request);
            String finalContent = finalResponse.getContent();
            
            System.out.println("\n最终回复:");
            System.out.println(finalContent);
        }
        
        // 测试流式工具调用
        System.out.println("\n\n=== 流式工具调用测试 ===");
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
        
        // 可以添加更多工具...
        
        return tools;
    }
    
    // 模拟执行工具调用
    private static String executeToolCall(ChatMessage.ToolCall toolCall) {
        String functionName = toolCall.getFunction().getName();
        String arguments = toolCall.getFunction().getArguments();
        
        if ("get_weather".equals(functionName)) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode args = mapper.readValue(arguments, ObjectNode.class);
                String location = args.get("location").asText();
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
                return "获取天气失败：" + e.getMessage();
            }
        }
        
        return "未知工具：" + functionName;
    }
    
    // 测试流式工具调用
    private static void testStreamingToolCall(OpenAI client, String modelId) throws Exception {
        System.out.println("测试流式响应中的工具调用...");
        
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
        StringBuilder toolCallBuilder = new StringBuilder();
        final ChatCompletionResponse.Usage[] finalUsage = {null};
        
        System.out.println("\n流式响应:");
        
        client.createChatCompletionStream(
            request,
            chunk -> {
                // 打印完整的chunk信息用于调试
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    System.out.println("\n[CHUNK]: " + mapper.writeValueAsString(chunk));
                } catch (Exception e) {
                    System.err.println("Failed to serialize chunk: " + e.getMessage());
                }
                
                // 处理内容
                String content = chunk.getContent();
                if (content != null && !content.isEmpty()) {
                    System.out.print("[CONTENT]: " + content);
                }
                
                // 处理工具调用
                if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                    ChatCompletionChunk.Delta delta = chunk.getChoices().get(0).getDelta();
                    if (delta != null) {
                        List<ChatMessage.ToolCall> toolCalls = delta.getToolCalls();
                        if (toolCalls != null && !toolCalls.isEmpty()) {
                            System.out.println("\n[TOOL CALLS DETECTED]");
                            for (ChatMessage.ToolCall toolCall : toolCalls) {
                                if (toolCall.getFunction() != null && 
                                    toolCall.getFunction().getArguments() != null) {
                                    System.out.println("  Tool: " + toolCall.getFunction().getName());
                                    System.out.println("  Args: " + toolCall.getFunction().getArguments());
                                    toolCallBuilder.append(toolCall.getFunction().getArguments());
                                }
                            }
                        }
                    }
                }
                
                // 检查usage信息
                if (chunk.getUsage() != null) {
                    System.out.println("\n[USAGE]: " + chunk.getUsage());
                    finalUsage[0] = chunk.getUsage();
                }
            },
            () -> {
                System.out.println("\n\n流式响应完成");
                if (toolCallBuilder.length() > 0) {
                    System.out.println("工具调用参数: " + toolCallBuilder.toString());
                }
                
                // 显示最终的usage信息
                if (finalUsage[0] != null) {
                    System.out.println("\n最终Usage信息:");
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