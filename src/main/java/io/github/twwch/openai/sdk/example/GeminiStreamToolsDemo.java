package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gemini流式工具调用完整示例
 * 展示如何在流式响应中处理工具调用
 */
public class GeminiStreamToolsDemo {
    
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
            
            // 运行流式工具调用示例
            System.out.println("=== Gemini 流式工具调用示例 ===\n");
            runStreamingToolExample(client);
            
        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 流式工具调用完整示例
     */
    private static void runStreamingToolExample(OpenAI client) throws Exception {
        // 创建工具定义
        List<ChatCompletionRequest.Tool> tools = createTools();
        
        // 初始化消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("你是一个有用的助手，可以查询天气、计算数学和搜索信息。"));
        messages.add(ChatMessage.user("帮我查一下北京和上海的天气，然后计算这两个城市温度的平均值"));
        
        // 创建请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gemini-2.0-flash");
        request.setMessages(messages);
        request.setTools(tools);
        request.setToolChoice("auto");
        request.setStream(true); // 启用流式
        
        System.out.println("发送请求: " + messages.get(messages.size() - 1).getContentAsString());
        System.out.println("\n开始流式响应...");
        System.out.println("----------------------------------------");
        
        // 第一轮：获取工具调用
        StreamResponseCollector collector = new StreamResponseCollector();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean hasToolCalls = new AtomicBoolean(false);
        
        client.createChatCompletionStream(request,
            chunk -> {
                collector.processChunk(chunk);
                // 检查是否有工具调用
                if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                    ChatCompletionChunk.Delta delta = chunk.getChoices().get(0).getDelta();
                    if (delta != null && delta.getToolCalls() != null && !delta.getToolCalls().isEmpty()) {
                        hasToolCalls.set(true);
                    }
                }
            },
            () -> {
                System.out.println("\n----------------------------------------");
                latch.countDown();
            },
            error -> {
                System.err.println("错误: " + error.getMessage());
                error.printStackTrace();
                latch.countDown();
            }
        );
        
        // 等待第一轮完成
        latch.await();
        
        // 处理工具调用
        if (hasToolCalls.get()) {
            System.out.println("\n检测到工具调用，正在处理...");
            
            // 获取完整的工具调用信息
            List<ToolCallInfo> toolCalls = collector.getCompletedToolCalls();
            System.out.println("工具调用数量: " + toolCalls.size());
            
            // 构建助手消息（包含工具调用）
            ChatMessage assistantMessage = ChatMessage.assistant(collector.getContent());
            ChatMessage.ToolCall[] toolCallArray = new ChatMessage.ToolCall[toolCalls.size()];
            for (int i = 0; i < toolCalls.size(); i++) {
                ToolCallInfo info = toolCalls.get(i);
                ChatMessage.ToolCall toolCall = new ChatMessage.ToolCall();
                toolCall.setId(info.id);
                toolCall.setType("function");
                ChatMessage.ToolCall.Function function = new ChatMessage.ToolCall.Function();
                function.setName(info.name);
                function.setArguments(info.arguments);
                toolCall.setFunction(function);
                toolCallArray[i] = toolCall;
                
                System.out.println("\n工具 #" + (i + 1) + ":");
                System.out.println("  ID: " + info.id);
                System.out.println("  名称: " + info.name);
                System.out.println("  参数: " + info.arguments);
            }
            assistantMessage.setToolCalls(toolCallArray);
            messages.add(assistantMessage);
            
            // 执行工具并添加结果
            System.out.println("\n执行工具...");
            for (ChatMessage.ToolCall toolCall : toolCallArray) {
                String result = executeToolMock(
                    toolCall.getFunction().getName(),
                    toolCall.getFunction().getArguments()
                );
                System.out.println("\n工具 '" + toolCall.getFunction().getName() + "' 执行结果: " + result);
                
                // 添加工具结果消息
                messages.add(ChatMessage.tool(
                    toolCall.getFunction().getName(),
                    toolCall.getId(),
                    result
                ));
            }
            
            // 第二轮：获取最终响应
            System.out.println("\n获取最终响应...");
            System.out.println("----------------------------------------");
            
            request.setMessages(messages);
            request.setTools(null); // 清除工具，获取最终答案
            
            CountDownLatch finalLatch = new CountDownLatch(1);
            StringBuilder finalResponse = new StringBuilder();
            
            client.createChatCompletionStream(request,
                chunk -> {
                    String content = chunk.getContent();
                    if (content != null) {
                        System.out.print(content);
                        finalResponse.append(content);
                    }
                },
                () -> {
                    System.out.println("\n----------------------------------------");
                    System.out.println("流式响应完成！");
                    finalLatch.countDown();
                },
                error -> {
                    System.err.println("错误: " + error.getMessage());
                    error.printStackTrace();
                    finalLatch.countDown();
                }
            );
            
            finalLatch.await();
            
        } else {
            // 直接输出响应内容
            System.out.println("\n最终响应:");
            System.out.println(collector.getContent());
        }
    }
    
    /**
     * 流式响应收集器
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
                        // 对于Gemini，使用index来区分不同的工具调用
                        Integer index = toolCall.getIndex();
                        String toolId = toolCall.getId();
                        
                        // 生成唯一的工具ID
                        String uniqueId;
                        if (index != null) {
                            uniqueId = "tool_index_" + index;
                        } else if (toolId != null && !toolId.isEmpty()) {
                            uniqueId = toolId;
                        } else {
                            // 使用函数名和当前size生成ID
                            String funcName = (toolCall.getFunction() != null && toolCall.getFunction().getName() != null) 
                                ? toolCall.getFunction().getName() 
                                : "unknown";
                            uniqueId = "tool_" + funcName + "_" + toolCallBuilders.size();
                        }
                        
                        final String finalToolId = uniqueId;
                        ToolCallBuilder builder = toolCallBuilders.computeIfAbsent(
                            finalToolId, k -> new ToolCallBuilder(toolId != null ? toolId : finalToolId)
                        );
                        
                        if (toolCall.getFunction() != null) {
                            if (toolCall.getFunction().getName() != null) {
                                builder.setName(toolCall.getFunction().getName());
                            }
                            if (toolCall.getFunction().getArguments() != null) {
                                String args = toolCall.getFunction().getArguments();
                                // 检查是否是完整的JSON参数，如果是新的调用，重置参数
                                if (args.startsWith("{") && builder.hasArguments() && builder.getArguments().endsWith("}")) {
                                    // 这是一个新的完整参数，可能是新的工具调用
                                    String newUniqueId = finalToolId + "_" + System.nanoTime();
                                    builder = new ToolCallBuilder(toolId != null ? toolId : newUniqueId);
                                    builder.setName(toolCall.getFunction().getName());
                                    toolCallBuilders.put(newUniqueId, builder);
                                }
                                builder.appendArguments(args);
                            }
                        }
                    }
                }
            }
        }
        
        public String getContent() {
            return content.toString();
        }
        
        public List<ToolCallInfo> getCompletedToolCalls() {
            List<ToolCallInfo> result = new ArrayList<>();
            for (ToolCallBuilder builder : toolCallBuilders.values()) {
                result.add(builder.build());
            }
            return result;
        }
        
        public void printSummary() {
            System.out.println("\n流式响应统计:");
            System.out.println("- 数据块数量: " + chunkCount);
            System.out.println("- 内容长度: " + content.length() + " 字符");
            System.out.println("- 工具调用数量: " + toolCallBuilders.size());
        }
    }
    
    /**
     * 工具调用构建器
     */
    static class ToolCallBuilder {
        private final String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
        
        ToolCallBuilder(String id) {
            this.id = id;
        }
        
        void setName(String name) {
            this.name = name;
        }
        
        void appendArguments(String args) {
            arguments.append(args);
        }
        
        boolean hasArguments() {
            return arguments.length() > 0;
        }
        
        String getArguments() {
            return arguments.toString();
        }
        
        ToolCallInfo build() {
            return new ToolCallInfo(id, name, arguments.toString());
        }
    }
    
    /**
     * 工具调用信息
     */
    static class ToolCallInfo {
        final String id;
        final String name;
        final String arguments;
        
        ToolCallInfo(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
    }
    
    // ========== 工具定义和执行 ==========
    
    /**
     * 创建工具集
     */
    private static List<ChatCompletionRequest.Tool> createTools() {
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        
        // 天气工具
        Map<String, Object> weatherParams = new HashMap<>();
        Map<String, Object> weatherProps = new HashMap<>();
        Map<String, Object> cityProp = new HashMap<>();
        cityProp.put("type", "string");
        cityProp.put("description", "城市名称");
        weatherProps.put("city", cityProp);
        weatherParams.put("type", "object");
        weatherParams.put("properties", weatherProps);
        weatherParams.put("required", Arrays.asList("city"));
        
        ChatCompletionRequest.Tool weatherTool = new ChatCompletionRequest.Tool();
        weatherTool.setType("function");
        ChatCompletionRequest.Function weatherFunc = new ChatCompletionRequest.Function();
        weatherFunc.setName("get_weather");
        weatherFunc.setDescription("获取指定城市的天气信息");
        weatherFunc.setParameters(weatherParams);
        weatherTool.setFunction(weatherFunc);
        tools.add(weatherTool);
        
        // 计算器工具
        Map<String, Object> calcParams = new HashMap<>();
        Map<String, Object> calcProps = new HashMap<>();
        Map<String, Object> exprProp = new HashMap<>();
        exprProp.put("type", "string");
        exprProp.put("description", "数学表达式");
        calcProps.put("expression", exprProp);
        calcParams.put("type", "object");
        calcParams.put("properties", calcProps);
        calcParams.put("required", Arrays.asList("expression"));
        
        ChatCompletionRequest.Tool calcTool = new ChatCompletionRequest.Tool();
        calcTool.setType("function");
        ChatCompletionRequest.Function calcFunc = new ChatCompletionRequest.Function();
        calcFunc.setName("calculate");
        calcFunc.setDescription("计算数学表达式");
        calcFunc.setParameters(calcParams);
        calcTool.setFunction(calcFunc);
        tools.add(calcTool);
        
        // 搜索工具
        Map<String, Object> searchParams = new HashMap<>();
        Map<String, Object> searchProps = new HashMap<>();
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "搜索查询");
        searchProps.put("query", queryProp);
        searchParams.put("type", "object");
        searchParams.put("properties", searchProps);
        searchParams.put("required", Arrays.asList("query"));
        
        ChatCompletionRequest.Tool searchTool = new ChatCompletionRequest.Tool();
        searchTool.setType("function");
        ChatCompletionRequest.Function searchFunc = new ChatCompletionRequest.Function();
        searchFunc.setName("search");
        searchFunc.setDescription("搜索信息");
        searchFunc.setParameters(searchParams);
        searchTool.setFunction(searchFunc);
        tools.add(searchTool);
        
        return tools;
    }
    
    /**
     * 模拟工具执行
     */
    private static String executeToolMock(String name, String arguments) {
        switch (name) {
            case "get_weather":
                if (arguments.contains("北京") || arguments.contains("Beijing")) {
                    return "{\"temperature\": 28, \"condition\": \"晴朗\", \"humidity\": 45}";
                } else if (arguments.contains("上海") || arguments.contains("Shanghai")) {
                    return "{\"temperature\": 30, \"condition\": \"多云\", \"humidity\": 65}";
                } else {
                    return "{\"temperature\": 25, \"condition\": \"未知\", \"humidity\": 50}";
                }
                
            case "calculate":
                // 简单计算平均值
                return "{\"result\": 29, \"calculation\": \"(28 + 30) / 2 = 29\"}";
                
            case "search":
                return "{\"results\": [\"相关信息1\", \"相关信息2\", \"相关信息3\"]}";
                
            default:
                return "{\"error\": \"未知工具: " + name + "\"}";
        }
    }
}