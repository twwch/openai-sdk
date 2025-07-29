package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.service.OpenAIService;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具调用示例 - 展示如何正确处理tool消息
 */
public class ToolCallExample {
    
    public static void main(String[] args) {
        try {
            // 创建服务实例
            AzureOpenAIConfig config = new AzureOpenAIConfig(
                System.getenv("AZURE_OPENAI_API_KEY"),
                System.getenv("AZURE_OPENAI_ENDPOINT"),
                System.getenv("AZURE_OPENAI_DEPLOYMENT_ID")
            );
            
            OpenAIService service = new OpenAIService(config);
            
            // 构建正确的消息历史
            ChatCompletionRequest request = buildCorrectRequest();
            
            // 发送请求
            var response = service.createChatCompletion(request);
            System.out.println("响应: " + response.getChoices().get(0).getMessage().getContent());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static ChatCompletionRequest buildCorrectRequest() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4");
        
        List<ChatMessage> messages = new ArrayList<>();
        
        // 1. 系统消息
        messages.add(ChatMessage.system("You are a helpful AI assistant."));
        
        // 2. 用户询问北京天气
        messages.add(ChatMessage.user("北京的天气如何"));
        
        // 3. 助手调用工具（包含tool_calls）
        ChatMessage assistantWithToolCall = ChatMessage.assistant(null); // content可以为null
        ChatMessage.ToolCall[] toolCalls = new ChatMessage.ToolCall[1];
        ChatMessage.ToolCall toolCall = new ChatMessage.ToolCall();
        toolCall.setId("call_i7ZyqXqZilZOynytjGl0Isd7");
        toolCall.setType("function");
        
        ChatMessage.ToolCall.Function function = new ChatMessage.ToolCall.Function();
        function.setName("get_current_weather");
        function.setArguments("{\"city\":\"Beijing\"}");
        toolCall.setFunction(function);
        
        toolCalls[0] = toolCall;
        assistantWithToolCall.setToolCalls(toolCalls);
        messages.add(assistantWithToolCall);
        
        // 4. 工具响应消息 - 使用正确的tool_call_id
        String weatherData = "{\"city\":\"Beijing\",\"forecasts\":\"#### City: Beijing\\n| Date | Temperature | Conditions |\\n|------|-------------|------------|\\n| 2025-07-29 | 29.9°C | scattered clouds |\"}";
        ChatMessage toolResponse = ChatMessage.tool("call_i7ZyqXqZilZOynytjGl0Isd7", weatherData);
        messages.add(toolResponse);
        
        // 5. 用户询问上海天气
        messages.add(ChatMessage.user("上海的天气如何"));
        
        request.setMessages(messages);
        
        // 添加工具定义（只添加必要的工具）
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        tools.add(createWeatherTool());
        request.setTools(tools);
        request.setToolChoice("auto");
        
        return request;
    }
    
    private static ChatCompletionRequest.Tool createWeatherTool() {
        ChatCompletionRequest.Tool tool = new ChatCompletionRequest.Tool();
        tool.setType("function");
        
        ChatCompletionRequest.Function function = new ChatCompletionRequest.Function();
        function.setName("get_current_weather");
        function.setDescription("Get the current weather in a given location");
        
        // 参数定义
        java.util.Map<String, Object> parameters = new java.util.HashMap<>();
        parameters.put("type", "object");
        
        java.util.Map<String, Object> properties = new java.util.HashMap<>();
        java.util.Map<String, Object> cityProp = new java.util.HashMap<>();
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