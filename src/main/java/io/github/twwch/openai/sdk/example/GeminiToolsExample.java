package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Gemini工具调用示例
 * 展示如何使用工具调用功能与Gemini模型交互
 */
public class GeminiToolsExample {
    
    public static void main(String[] args) {
        // 从环境变量获取API密钥
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("请设置环境变量 GEMINI_API_KEY");
            System.err.println("获取API密钥: https://ai.google.dev/");
            return;
        }
        
        try {
            // 创建Gemini客户端
            OpenAI client = OpenAI.gemini(apiKey);
            
            // 示例1: 基础工具调用 - 天气查询
            System.out.println("=== 示例1: 天气查询工具调用 ===");
            weatherToolExample(client);
            
            // 示例2: 多工具调用
            System.out.println("\n=== 示例2: 多工具并行调用 ===");
            multipleToolsExample(client);
            
            // 示例3: 流式工具调用
            System.out.println("\n=== 示例3: 流式工具调用 ===");
            streamingToolExample(client);
            
            // 示例4: 复杂工具编排
            System.out.println("\n=== 示例4: 复杂工具编排 ===");
            complexToolOrchestrationExample(client);
            
        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 天气查询工具调用示例
     */
    private static void weatherToolExample(OpenAI client) throws Exception {
        // 创建工具定义
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        tools.add(createWeatherTool());
        
        // 创建请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gemini-2.0-flash");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("北京今天的天气怎么样？"));
        request.setMessages(messages);
        request.setTools(tools);
        
        // 让模型自动选择工具
        request.setToolChoice("auto");
        
        // 发送请求
        ChatCompletionResponse response = client.createChatCompletion(request);
        
        // 检查是否有工具调用
        ChatMessage assistantMessage = response.getMessage();
        if (assistantMessage != null && assistantMessage.getToolCalls() != null && assistantMessage.getToolCalls().length > 0) {
            System.out.println("模型调用了工具:");
            for (ChatMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                System.out.println("- 工具: " + toolCall.getFunction().getName());
                System.out.println("  参数: " + toolCall.getFunction().getArguments());
                
                // 模拟工具执行结果
                String toolResult = executeWeatherTool(toolCall.getFunction().getArguments());
                
                // 添加工具结果到对话
                messages.add(assistantMessage);
                messages.add(ChatMessage.tool(toolCall.getId(), toolResult));
                
                // 再次调用获取最终响应
                request.setMessages(messages);
                request.setTools(null); // 清除工具，获取最终回答
                ChatCompletionResponse finalResponse = client.createChatCompletion(request);
                System.out.println("\n最终回答: " + finalResponse.getContent());
            }
        } else {
            System.out.println("回答: " + response.getContent());
        }
    }
    
    /**
     * 多工具并行调用示例
     */
    private static void multipleToolsExample(OpenAI client) throws Exception {
        // 创建多个工具
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        tools.add(createWeatherTool());
        tools.add(createCalculatorTool());
        tools.add(createSearchTool());
        
        // 创建请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gemini-2.0-flash");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("帮我查一下北京和上海的天气，然后计算两个城市温度的平均值"));
        request.setMessages(messages);
        request.setTools(tools);
        request.setToolChoice("auto");
        
        // 发送请求
        ChatCompletionResponse response = client.createChatCompletion(request);
        
        ChatMessage assistantMessage = response.getMessage();
        if (assistantMessage != null && assistantMessage.getToolCalls() != null && assistantMessage.getToolCalls().length > 0) {
            System.out.println("模型并行调用了 " + assistantMessage.getToolCalls().length + " 个工具:");
            
            // 添加助手消息
            messages.add(assistantMessage);
            
            // 处理每个工具调用
            for (ChatMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                System.out.println("- 工具: " + toolCall.getFunction().getName());
                System.out.println("  参数: " + toolCall.getFunction().getArguments());
                
                // 模拟执行工具
                String result = executeToolByName(
                    toolCall.getFunction().getName(),
                    toolCall.getFunction().getArguments()
                );
                
                // 添加工具结果
                messages.add(ChatMessage.tool(result, toolCall.getId()));
            }
            
            // 获取最终响应
            request.setMessages(messages);
            request.setTools(null);
            ChatCompletionResponse finalResponse = client.createChatCompletion(request);
            System.out.println("\n最终回答: " + finalResponse.getContent());
        }
    }
    
    /**
     * 流式工具调用示例
     */
    private static void streamingToolExample(OpenAI client) throws Exception {
        // 创建工具
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        tools.add(createWeatherTool());
        tools.add(createSearchTool());
        
        // 创建请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gemini-2.0-flash");
        request.setStream(true); // 启用流式
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("搜索一下最新的AI发展趋势，并告诉我旧金山的天气"));
        request.setMessages(messages);
        request.setTools(tools);
        request.setToolChoice("auto");
        
        // 使用CountDownLatch等待完成
        CountDownLatch latch = new CountDownLatch(1);
        
        // 响应收集器
        StreamResponseCollector collector = new StreamResponseCollector();
        
        System.out.println("开始流式响应:");
        System.out.println("----------------------------------------");
        
        // 发送流式请求
        client.createChatCompletionStream(request,
            // onChunk - 处理每个数据块
            chunk -> {
                collector.processChunk(chunk);
            },
            // onComplete - 完成时的回调
            () -> {
                System.out.println("\n----------------------------------------");
                System.out.println("流式响应完成!");
                collector.printSummary();
                latch.countDown();
            },
            // onError - 错误时的回调
            error -> {
                System.err.println("\n流式请求错误: " + error.getMessage());
                error.printStackTrace();
                latch.countDown();
            }
        );
        
        // 等待完成
        latch.await();
    }
    
    /**
     * 复杂工具编排示例 - 旅行助手
     */
    private static void complexToolOrchestrationExample(OpenAI client) throws Exception {
        // 创建旅行相关工具
        List<ChatCompletionRequest.Tool> tools = createTravelTools();
        
        // 设置系统提示
        String systemPrompt = "你是一个专业的旅行助手，可以帮助用户规划旅行、查询天气、搜索景点、计算预算等。" +
                            "请根据用户的需求，合理使用提供的工具来完成任务。";
        
        // 创建请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gemini-2.0-flash");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user("我想下周去东京旅行3天，帮我查一下天气、推荐景点，并估算一下预算"));
        request.setMessages(messages);
        request.setTools(tools);
        request.setToolChoice("auto");
        
        // 多轮对话处理
        int maxRounds = 3; // 最多3轮工具调用
        for (int round = 0; round < maxRounds; round++) {
            System.out.println("\n第 " + (round + 1) + " 轮工具调用:");
            
            ChatCompletionResponse response = client.createChatCompletion(request);
            
            ChatMessage assistantMessage = response.getMessage();
            if (assistantMessage != null && assistantMessage.getToolCalls() != null && assistantMessage.getToolCalls().length > 0) {
                System.out.println("调用了 " + assistantMessage.getToolCalls().length + " 个工具:");
                
                messages.add(assistantMessage);
                
                for (ChatMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                    System.out.println("- " + toolCall.getFunction().getName());
                    String result = executeTravelTool(
                        toolCall.getFunction().getName(),
                        toolCall.getFunction().getArguments()
                    );
                    messages.add(ChatMessage.tool(result, toolCall.getId()));
                }
                
                request.setMessages(messages);
            } else {
                // 没有工具调用，输出最终结果
                System.out.println("\n旅行助手回答:");
                System.out.println(response.getContent());
                break;
            }
        }
    }
    
    /**
     * 响应收集器 - 收集流式响应
     */
    static class StreamResponseCollector {
        private StringBuilder content = new StringBuilder();
        private Map<String, ToolCallBuilder> toolCallBuilders = new LinkedHashMap<>();
        private int chunkCount = 0;
        
        public void processChunk(ChatCompletionChunk chunk) {
            chunkCount++;
            
            // 处理内容
            String chunkContent = chunk.getContent();
            if (chunkContent != null) {
                System.out.print(chunkContent);
                content.append(chunkContent);
            }
            
            // 处理工具调用
            if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                ChatCompletionChunk.Delta delta = chunk.getChoices().get(0).getDelta();
                if (delta != null && delta.getToolCalls() != null) {
                    for (ChatMessage.ToolCall toolCall : delta.getToolCalls()) {
                        String toolId = toolCall.getId();
                        
                        if (toolId != null) {
                            ToolCallBuilder builder = toolCallBuilders.computeIfAbsent(
                                toolId, k -> new ToolCallBuilder(toolId)
                            );
                            
                            if (toolCall.getFunction() != null) {
                                if (toolCall.getFunction().getName() != null) {
                                    builder.setName(toolCall.getFunction().getName());
                                }
                                if (toolCall.getFunction().getArguments() != null) {
                                    builder.appendArguments(toolCall.getFunction().getArguments());
                                }
                            }
                        }
                    }
                }
            }
        }
        
        public void printSummary() {
            System.out.println("\n收集到的数据:");
            System.out.println("- 数据块数量: " + chunkCount);
            System.out.println("- 总内容长度: " + content.length() + " 字符");
            
            if (!toolCallBuilders.isEmpty()) {
                System.out.println("\n检测到工具调用:");
                for (ToolCallBuilder builder : toolCallBuilders.values()) {
                    System.out.println("- 工具ID: " + builder.id);
                    System.out.println("  名称: " + builder.name);
                    System.out.println("  参数: " + builder.arguments);
                }
            }
        }
    }
    
    /**
     * 工具调用构建器
     */
    static class ToolCallBuilder {
        String id;
        String name;
        StringBuilder arguments = new StringBuilder();
        
        ToolCallBuilder(String id) {
            this.id = id;
        }
        
        void setName(String name) {
            this.name = name;
        }
        
        void appendArguments(String args) {
            arguments.append(args);
        }
    }
    
    // ========== 工具定义 ==========
    
    /**
     * 创建天气查询工具
     */
    private static ChatCompletionRequest.Tool createWeatherTool() {
        Map<String, Object> parameters = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> cityProp = new HashMap<>();
        cityProp.put("type", "string");
        cityProp.put("description", "城市名称，如：北京、上海、东京");
        properties.put("city", cityProp);
        
        Map<String, Object> daysProp = new HashMap<>();
        daysProp.put("type", "integer");
        daysProp.put("description", "查询天数，默认为1天");
        daysProp.put("minimum", 1);
        daysProp.put("maximum", 7);
        properties.put("days", daysProp);
        
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("city"));
        
        ChatCompletionRequest.Tool tool = new ChatCompletionRequest.Tool();
        tool.setType("function");
        ChatCompletionRequest.Function function = new ChatCompletionRequest.Function();
        function.setName("get_weather");
        function.setDescription("获取指定城市的天气信息");
        function.setParameters(parameters);
        tool.setFunction(function);
        
        return tool;
    }
    
    /**
     * 创建计算器工具
     */
    private static ChatCompletionRequest.Tool createCalculatorTool() {
        Map<String, Object> parameters = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> expressionProp = new HashMap<>();
        expressionProp.put("type", "string");
        expressionProp.put("description", "要计算的数学表达式");
        properties.put("expression", expressionProp);
        
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("expression"));
        
        ChatCompletionRequest.Tool tool = new ChatCompletionRequest.Tool();
        tool.setType("function");
        ChatCompletionRequest.Function function = new ChatCompletionRequest.Function();
        function.setName("calculator");
        function.setDescription("执行数学计算");
        function.setParameters(parameters);
        tool.setFunction(function);
        
        return tool;
    }
    
    /**
     * 创建搜索工具
     */
    private static ChatCompletionRequest.Tool createSearchTool() {
        Map<String, Object> parameters = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "搜索查询内容");
        properties.put("query", queryProp);
        
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("query"));
        
        ChatCompletionRequest.Tool tool = new ChatCompletionRequest.Tool();
        tool.setType("function");
        ChatCompletionRequest.Function function = new ChatCompletionRequest.Function();
        function.setName("search");
        function.setDescription("搜索互联网信息");
        function.setParameters(parameters);
        tool.setFunction(function);
        
        return tool;
    }
    
    /**
     * 创建旅行相关工具集
     */
    private static List<ChatCompletionRequest.Tool> createTravelTools() {
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        
        // 天气工具
        tools.add(createWeatherTool());
        
        // 景点搜索工具
        Map<String, Object> attractionParams = new HashMap<>();
        Map<String, Object> attractionProps = new HashMap<>();
        Map<String, Object> cityProp = new HashMap<>();
        cityProp.put("type", "string");
        cityProp.put("description", "城市名称");
        attractionProps.put("city", cityProp);
        attractionParams.put("type", "object");
        attractionParams.put("properties", attractionProps);
        attractionParams.put("required", Arrays.asList("city"));
        
        ChatCompletionRequest.Tool attractionTool = new ChatCompletionRequest.Tool();
        attractionTool.setType("function");
        ChatCompletionRequest.Function attractionFunc = new ChatCompletionRequest.Function();
        attractionFunc.setName("search_attractions");
        attractionFunc.setDescription("搜索城市的热门景点");
        attractionFunc.setParameters(attractionParams);
        attractionTool.setFunction(attractionFunc);
        tools.add(attractionTool);
        
        // 预算计算工具
        Map<String, Object> budgetParams = new HashMap<>();
        Map<String, Object> budgetProps = new HashMap<>();
        Map<String, Object> destinationProp = new HashMap<>();
        destinationProp.put("type", "string");
        destinationProp.put("description", "目的地");
        budgetProps.put("destination", destinationProp);
        Map<String, Object> daysProp = new HashMap<>();
        daysProp.put("type", "integer");
        daysProp.put("description", "旅行天数");
        budgetProps.put("days", daysProp);
        Map<String, Object> levelProp = new HashMap<>();
        levelProp.put("type", "string");
        levelProp.put("enum", Arrays.asList("budget", "standard", "luxury"));
        levelProp.put("description", "旅行档次");
        budgetProps.put("level", levelProp);
        budgetParams.put("type", "object");
        budgetParams.put("properties", budgetProps);
        budgetParams.put("required", Arrays.asList("destination", "days"));
        
        ChatCompletionRequest.Tool budgetTool = new ChatCompletionRequest.Tool();
        budgetTool.setType("function");
        ChatCompletionRequest.Function budgetFunc = new ChatCompletionRequest.Function();
        budgetFunc.setName("calculate_budget");
        budgetFunc.setDescription("计算旅行预算");
        budgetFunc.setParameters(budgetParams);
        budgetTool.setFunction(budgetFunc);
        tools.add(budgetTool);
        
        return tools;
    }
    
    // ========== 工具执行模拟 ==========
    
    /**
     * 执行天气工具（模拟）
     */
    private static String executeWeatherTool(String arguments) {
        // 解析参数并返回模拟结果
        return "{\"temperature\": 25, \"condition\": \"晴朗\", \"humidity\": 60, \"wind_speed\": 5}";
    }
    
    /**
     * 根据名称执行工具（模拟）
     */
    private static String executeToolByName(String name, String arguments) {
        switch (name) {
            case "get_weather":
                return "{\"temperature\": 25, \"condition\": \"晴朗\", \"humidity\": 60}";
            case "calculator":
                return "{\"result\": 25}"; // 模拟平均温度计算结果
            case "search":
                return "{\"results\": [\"AI正在改变世界\", \"机器学习新突破\", \"大语言模型应用\"]}";
            default:
                return "{\"error\": \"Unknown tool\"}";
        }
    }
    
    /**
     * 执行旅行工具（模拟）
     */
    private static String executeTravelTool(String name, String arguments) {
        switch (name) {
            case "get_weather":
                return "{\"forecast\": \"下周东京天气晴朗，温度18-25℃，适合旅行\"}";
            case "search_attractions":
                return "{\"attractions\": [\"东京塔\", \"浅草寺\", \"明治神宫\", \"秋叶原\", \"银座\"]}";
            case "calculate_budget":
                return "{\"budget\": {\"accommodation\": \"300美元\", \"food\": \"150美元\", \"transportation\": \"100美元\", \"attractions\": \"80美元\", \"total\": \"630美元\"}}";
            default:
                return "{\"result\": \"Tool executed successfully\"}";
        }
    }
}