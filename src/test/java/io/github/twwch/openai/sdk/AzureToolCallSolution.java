package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.service.OpenAIService;

import java.util.ArrayList;
import java.util.List;

/**
 * Azure OpenAI 工具调用解决方案
 */
public class AzureToolCallSolution {
    
    /**
     * 解决方案1: 减少工具数量
     */
    public static void solutionReduceTools(OpenAIService service) throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("You are a helpful assistant."));
        messages.add(ChatMessage.user("上海的天气如何"));
        request.setMessages(messages);
        
        // 只包含最相关的工具（比如前10个）
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        // 只添加必要的工具，不要超过10个
        tools.add(createTool("get_current_weather", "Get weather information"));
        tools.add(createTool("ai-website-summarizer", "Summarize websites"));
        // ... 最多添加8-10个工具
        
        request.setTools(tools);
        request.setToolChoice("auto");
        
        var response = service.createChatCompletion(request);
        System.out.println("响应: " + response.getChoices().get(0).getMessage().getContent());
    }
    
    /**
     * 解决方案2: 分批处理工具
     */
    public static void solutionBatchTools(OpenAIService service, List<ChatCompletionRequest.Tool> allTools) throws Exception {
        // 将工具分成多个批次，每批不超过10个
        int batchSize = 10;
        List<List<ChatCompletionRequest.Tool>> batches = new ArrayList<>();
        
        for (int i = 0; i < allTools.size(); i += batchSize) {
            batches.add(allTools.subList(i, Math.min(i + batchSize, allTools.size())));
        }
        
        // 根据用户输入选择最相关的批次
        // 这里需要一些逻辑来判断哪个批次的工具最相关
    }
    
    /**
     * 解决方案3: 使用函数调用而非工具调用（如果Azure支持）
     */
    public static void solutionUseFunctions(OpenAIService service) throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4");
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("You are a helpful assistant."));
        messages.add(ChatMessage.user("上海的天气如何"));
        request.setMessages(messages);
        
        // 使用functions而不是tools（旧版API）
        List<ChatCompletionRequest.Function> functions = new ArrayList<>();
        ChatCompletionRequest.Function weatherFunction = new ChatCompletionRequest.Function();
        weatherFunction.setName("get_current_weather");
        weatherFunction.setDescription("Get weather information");
        // 设置参数...
        functions.add(weatherFunction);
        
        request.setFunctions(functions);
        request.setFunctionCall("auto");
        
        var response = service.createChatCompletion(request);
        System.out.println("响应: " + response.getChoices().get(0).getMessage().getContent());
    }
    
    /**
     * 解决方案4: 清理消息历史
     */
    public static void solutionCleanMessages(OpenAIService service) throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4");
        
        // 不包含tool_calls和tool角色的消息
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("You are a helpful assistant."));
        messages.add(ChatMessage.user("北京的天气如何"));
        messages.add(ChatMessage.assistant("北京今天天气晴朗，温度29.9°C。"));
        messages.add(ChatMessage.user("上海的天气如何"));
        request.setMessages(messages);
        
        // 添加少量工具
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        tools.add(createTool("get_current_weather", "Get weather information"));
        request.setTools(tools);
        request.setToolChoice("auto");
        
        var response = service.createChatCompletion(request);
        System.out.println("响应: " + response.getChoices().get(0).getMessage().getContent());
    }
    
    private static ChatCompletionRequest.Tool createTool(String name, String description) {
        ChatCompletionRequest.Tool tool = new ChatCompletionRequest.Tool();
        tool.setType("function");
        
        ChatCompletionRequest.Function function = new ChatCompletionRequest.Function();
        function.setName(name);
        function.setDescription(description);
        // 设置参数schema...
        
        tool.setFunction(function);
        return tool;
    }
    
    public static void main(String[] args) {
        System.out.println("=== Azure OpenAI 工具调用解决方案 ===");
        System.out.println("1. 减少工具数量到10个以内");
        System.out.println("2. 移除消息历史中的tool_calls和tool角色消息");
        System.out.println("3. 检查模型是否支持工具调用");
        System.out.println("4. 考虑使用functions而不是tools（如果使用旧版API）");
        System.out.println("5. 验证tool参数的JSON schema格式是否正确");
    }
}