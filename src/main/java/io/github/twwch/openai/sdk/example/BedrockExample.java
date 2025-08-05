package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.Arrays;

/**
 * AWS Bedrock使用示例
 */
public class BedrockExample {
    public static void main(String[] args) {
        // 示例1：使用默认凭证（从AWS配置文件或环境变量获取）
        System.out.println("\n=== 示例2：使用访问密钥 ===");
        String accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
        String secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        
        if (accessKeyId != null && secretAccessKey != null) {
            // 尝试使用 Claude v2（更广泛支持的模型）
            OpenAI bedrockClient2 = OpenAI.bedrock("us-east-1", accessKeyId, secretAccessKey,
                                                   "anthropic.claude-v2");
            
            try {
                // 带系统提示的对话
                ChatCompletionResponse response = bedrockClient2.createChatCompletion("anthropic.claude-v2",
                    Arrays.asList(
                        ChatMessage.system("你是一个友好的AI助手。"),
                        ChatMessage.user("请用三句话介绍一下机器学习。")
                    )
                );
                
                System.out.println("模型: " + response.getModel());
                System.out.println("响应: " + response.getContent());
                System.out.println("使用的tokens: " + response.getUsage().getTotalTokens());
                
            } catch (Exception e) {
                System.err.println("错误: " + e.getMessage());
            }
        }
    }
}