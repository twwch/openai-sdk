package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.service.OpenAIService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用调试测试
 */
public class ToolCallDebugTest {
    
    public static void main(String[] args) {
        // 测试Azure OpenAI的工具调用限制
        testToolCallLimits();
    }
    
    private static void testToolCallLimits() {
        try {
            // 创建Azure配置
            AzureOpenAIConfig config = new AzureOpenAIConfig(
                System.getenv("AZURE_OPENAI_API_KEY"),
                System.getenv("AZURE_OPENAI_ENDPOINT"),
                System.getenv("AZURE_OPENAI_DEPLOYMENT_ID")
            );
            
            OpenAIService service = new OpenAIService(config);
            
            // 测试1: 单个工具
            System.out.println("测试1: 单个工具调用");
            testWithToolCount(service, 1);
            
            // 测试2: 5个工具
            System.out.println("\n测试2: 5个工具调用");
            testWithToolCount(service, 5);
            
            // 测试3: 10个工具
            System.out.println("\n测试3: 10个工具调用");
            testWithToolCount(service, 10);
            
            // 测试4: 测试你的实际场景（简化版）
            System.out.println("\n测试4: 实际场景测试");
            testActualScenario(service);
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testWithToolCount(OpenAIService service, int toolCount) {
        try {
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel("gpt-4");
            
            // 添加消息
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("You are a helpful assistant."));
            messages.add(ChatMessage.user("What's the weather in Shanghai?"));
            request.setMessages(messages);
            
            // 添加指定数量的工具
            List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
            for (int i = 0; i < toolCount; i++) {
                tools.add(createSampleTool("tool_" + i, "Tool " + i + " description"));
            }
            request.setTools(tools);
            request.setToolChoice("auto");
            
            // 发送请求
            System.out.println("发送包含 " + toolCount + " 个工具的请求...");
            var response = service.createChatCompletion(request);
            System.out.println("成功！响应: " + response.getChoices().get(0).getMessage().getContent());
            
        } catch (Exception e) {
            System.err.println("失败: " + e.getMessage());
        }
    }
    
    private static void testActualScenario(OpenAIService service) {
        try {
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel("gpt-4");
            
            // 构建你的实际消息历史
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("You are a helpful AI assistant."));
            messages.add(ChatMessage.assistant("Forgive that fumble! I got momentarily lost. Could you repeat your request? I'm focused and ready!"));
            messages.add(ChatMessage.user("北京的天气如何"));
            
            // 添加包含tool_calls的助手消息
            ChatMessage assistantMsg = ChatMessage.assistant("");
            // 注意：这里需要设置tool_calls，但你的ChatMessage类可能需要扩展来支持这个
            messages.add(assistantMsg);
            
            // 添加工具响应消息
            ChatMessage toolMsg = new ChatMessage();
            toolMsg.setRole("tool");
            toolMsg.setContent("{\"city\":\"Beijing\",\"forecasts\":\"...\"}");
            toolMsg.setName("call_i7ZyqXqZilZOynytjGl0Isd7");
            messages.add(toolMsg);
            
            messages.add(ChatMessage.user("上海呢"));
            messages.add(ChatMessage.user("<user_query>上海的天气如何</user_query>"));
            
            request.setMessages(messages);
            
            // 只添加少量工具进行测试
            List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
            tools.add(createWeatherTool());
            tools.add(createSampleTool("video-summarizer", "Generates concise summaries from uploaded videos"));
            request.setTools(tools);
            request.setToolChoice("auto");
            
            System.out.println("发送实际场景请求...");
            var response = service.createChatCompletion(request);
            System.out.println("成功！响应: " + response.getChoices().get(0).getMessage().getContent());
            
        } catch (Exception e) {
            System.err.println("失败: " + e.getMessage());
        }
    }
    
    private static ChatCompletionRequest.Tool createSampleTool(String name, String description) {
        ChatCompletionRequest.Tool tool = new ChatCompletionRequest.Tool();
        tool.setType("function");
        
        ChatCompletionRequest.Function function = new ChatCompletionRequest.Function();
        function.setName(name);
        function.setDescription(description);
        
        // 创建参数schema
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "user query");
        properties.put("query", queryProp);
        
        parameters.put("properties", properties);
        parameters.put("required", new String[]{"query"});
        
        function.setParameters(parameters);
        tool.setFunction(function);
        
        return tool;
    }
    
    private static ChatCompletionRequest.Tool createWeatherTool() {
        ChatCompletionRequest.Tool tool = new ChatCompletionRequest.Tool();
        tool.setType("function");
        
        ChatCompletionRequest.Function function = new ChatCompletionRequest.Function();
        function.setName("get_current_weather");
        function.setDescription("Get the current weather in a given location");
        
        // 创建参数schema
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> cityProp = new HashMap<>();
        cityProp.put("type", "string");
        cityProp.put("description", "The city name");
        properties.put("city", cityProp);
        
        parameters.put("properties", properties);
        parameters.put("required", new String[]{"city"});
        
        function.setParameters(parameters);
        tool.setFunction(function);
        
        return tool;
    }
}