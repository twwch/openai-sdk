package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.ModelInfo;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Azure OpenAI SDK使用示例
 */
public class AzureOpenAIExample {
    public static void main(String[] args) {
        // 替换为你的Azure OpenAI API密钥
        String apiKey = "your-azure-api-key";
        
        // 替换为你的Azure OpenAI资源名称
        String resourceName = "your-resource-name";
        
        // 替换为你的Azure OpenAI部署ID
        String deploymentId = "your-deployment-id";
        
        // 创建Azure OpenAI客户端
        OpenAI openai = OpenAI.azure(apiKey, resourceName, deploymentId);
        
        try {
            // 示例1：列出所有可用模型
            System.out.println("获取可用模型列表：");
            List<ModelInfo> models = openai.listModels();
            for (ModelInfo model : models) {
                System.out.println(model.getId());
            }
            System.out.println();
            
            // 示例2：使用最简单的方式进行聊天
            // 注意：在Azure OpenAI中，模型参数会被忽略，使用的是部署ID
            System.out.println("简单聊天示例：");
            String response = openai.chat("gpt-3.5-turbo", "你好，请介绍一下自己。");
            System.out.println("AI回复: " + response);
            System.out.println();
            
            // 示例3：使用系统提示和用户提示
            System.out.println("带系统提示的聊天示例：");
            response = openai.chat("gpt-3.5-turbo", "你是一个专业的Java开发者", "请解释Java中的多态性。");
            System.out.println("AI回复: " + response);
            System.out.println();
            
            // 示例4：使用完整的聊天消息列表
            System.out.println("完整聊天消息列表示例：");
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("你是一个专业的Java开发者"));
            messages.add(ChatMessage.user("什么是设计模式？"));
            messages.add(ChatMessage.assistant("设计模式是软件开发中常见问题的典型解决方案。它们是经过测试的、可重用的代码设计，可以解决特定的设计问题。"));
            messages.add(ChatMessage.user("请举例说明单例模式。"));
            
            ChatCompletionResponse chatResponse = openai.createChatCompletion("gpt-3.5-turbo", messages);
            System.out.println("AI回复: " + chatResponse.getContent());
            System.out.println("使用令牌: " + chatResponse.getUsage().getTotalTokens());
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}