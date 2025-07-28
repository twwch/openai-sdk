package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.ModelInfo;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI SDK使用示例
 */
public class OpenAIExample {
    public static void main(String[] args) {
        // 替换为你的API密钥
        String apiKey = "";
        String baseUrl = "";
        String model = "";

        // 创建OpenAI客户端
        OpenAI openai = new OpenAI(apiKey, baseUrl);
        
        try {
            // 示例1：列出所有可用模型
//            System.out.println("获取可用模型列表：");
//            List<ModelInfo> models = openai.listModels();
//            for (ModelInfo model : models) {
//                System.out.println(model.getId());
//            }
//            System.out.println();
//
//            // 示例2：获取特定模型的详细信息
//            System.out.println("获取模型详情：");
//            ModelInfo modelInfo = openai.getModel("gpt-3.5-turbo");
//            System.out.println("模型ID: " + modelInfo.getId());
//            System.out.println("所有者: " + modelInfo.getOwnedBy());
//            System.out.println();
            
            // 示例3：使用最简单的方式进行聊天
            System.out.println("简单聊天示例：");
            String response = openai.chat(model, "你好，请介绍一下自己。");
            System.out.println("AI回复: " + response);
            System.out.println();
            
//            // 示例4：使用系统提示和用户提示
//            System.out.println("带系统提示的聊天示例：");
//            response = openai.chat("gpt-3.5-turbo", "你是一个专业的Java开发者", "请解释Java中的多态性。");
//            System.out.println("AI回复: " + response);
//            System.out.println();
//
//            // 示例5：使用完整的聊天消息列表
            System.out.println("完整聊天消息列表示例：");
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("你是一个专业的Java开发者"));
            messages.add(ChatMessage.user("什么是设计模式？"));
            messages.add(ChatMessage.assistant("设计模式是软件开发中常见问题的典型解决方案。它们是经过测试的、可重用的代码设计，可以解决特定的设计问题。"));
            messages.add(ChatMessage.user("请举例说明单例模式。"));

            ChatCompletionResponse chatResponse = openai.createChatCompletion(model, messages);
            System.out.println("AI回复: " + chatResponse.getContent());
            System.out.println("使用令牌: " + chatResponse.getUsage().getTotalTokens());
            System.out.println();
//
            // 示例6：使用完整的请求配置
            System.out.println("完整请求配置示例：");
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel(model);
            request.setMessages(messages);
            request.setTemperature(0.7);
            request.setMaxTokens(500);
            request.setN(1);

            chatResponse = openai.createChatCompletion(request);
            System.out.println("AI回复: " + chatResponse.getContent());
            System.out.println("使用令牌: " + chatResponse.getUsage().getTotalTokens());
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}