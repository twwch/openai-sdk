package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public class BedrockToolCallSimpleTest {
    
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
        
        System.out.println("=== Bedrock 简单工具调用测试 ===");
        System.out.println("模型: " + modelId);
        
        // 测试1: 不使用工具的普通对话
        System.out.println("\n1. 测试普通对话（不使用工具）");
        testNormalChat(client, modelId);
        
        // 测试2: 使用工具的对话
        System.out.println("\n\n2. 测试工具调用");
        testToolCall(client, modelId);
        
        System.out.println("\n测试完成");
    }
    
    private static void testNormalChat(OpenAI client, String modelId) throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(Collections.singletonList(
            ChatMessage.user("你好，今天的日期是什么？")
        ));
        
        // 清除默认值
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogprobs(null);
        request.setServiceTier(null);
        
        ChatCompletionResponse response = client.createChatCompletion(request);
        System.out.println("回复: " + response.getContent());
    }
    
    private static void testToolCall(OpenAI client, String modelId) throws Exception {
        // 创建一个简单的工具
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        ChatCompletionRequest.Tool tool = new ChatCompletionRequest.Tool();
        tool.setType("function");
        
        ChatCompletionRequest.Function function = new ChatCompletionRequest.Function();
        function.setName("get_current_time");
        function.setDescription("获取当前时间");
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", new HashMap<>());
        parameters.put("required", Collections.emptyList());
        
        function.setParameters(parameters);
        tool.setFunction(function);
        tools.add(tool);
        
        // 创建请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(Collections.singletonList(
            ChatMessage.user("现在几点了？")
        ));
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
        
        System.out.println("发送带工具的请求...");
        
        // 打印请求JSON以便调试
        ObjectMapper mapper = new ObjectMapper();
        System.out.println("请求JSON: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(request));
        
        try {
            ChatCompletionResponse response = client.createChatCompletion(request);
            ChatMessage message = response.getChoices().get(0).getMessage();
            
            System.out.println("\n响应:");
            if (message.getContent() != null) {
                System.out.println("内容: " + message.getContent());
            }
            
            if (message.getToolCalls() != null && message.getToolCalls().length > 0) {
                System.out.println("工具调用:");
                for (ChatMessage.ToolCall toolCall : message.getToolCalls()) {
                    System.out.println("  - 工具: " + toolCall.getFunction().getName());
                    System.out.println("    参数: " + toolCall.getFunction().getArguments());
                }
            } else {
                System.out.println("（未检测到工具调用）");
            }
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}