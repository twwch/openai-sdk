package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试不同Claude模型的兼容性
 */
public class BedrockModelCompatibilityTest {
    
    public static void main(String[] args) throws Exception {
        // 从环境变量获取凭证
        String bearerKey = System.getenv("AWS_BEARER_KEY_BEDROCK");
        String bearerToken = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
        
        if (bearerKey == null || bearerToken == null) {
            System.err.println("请设置环境变量 AWS_BEARER_KEY_BEDROCK 和 AWS_BEARER_TOKEN_BEDROCK");
            System.exit(1);
        }
        
        String region = "us-east-2";
        
        // 测试不同的模型ID
        String[] modelIds = {
            // Claude 3.7 Sonnet (可能有问题的)
            "us.anthropic.claude-3-7-sonnet-20250219-v1:0",
            
            // Claude 3.5 Sonnet
            "anthropic.claude-3-5-sonnet-20240620-v1:0",
            
            // Claude 3 系列
            "anthropic.claude-3-sonnet-20240229",
            "anthropic.claude-3-haiku-20240307-v1:0",
            "anthropic.claude-3-opus-20240229-v1:0",
            
            // Claude 2 系列
            "anthropic.claude-v2:1",
            "anthropic.claude-v2",
            "anthropic.claude-instant-v1"
        };
        
        for (String modelId : modelIds) {
            System.out.println("\n==========================================");
            System.out.println("测试模型: " + modelId);
            System.out.println("==========================================");
            
            try {
                // 测试非流式请求
                testNonStreaming(region, bearerKey, bearerToken, modelId);
                
                // 测试流式请求
                testStreaming(region, bearerKey, bearerToken, modelId);
                
                System.out.println("✅ 模型 " + modelId + " 测试通过");
                
            } catch (Exception e) {
                System.err.println("❌ 模型 " + modelId + " 测试失败");
                System.err.println("   错误: " + e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("METRIC_VALUES")) {
                    System.err.println("   可能原因: 模型不支持on-demand throughput");
                }
            }
        }
    }
    
    // 测试非流式请求
    private static void testNonStreaming(String region, String bearerKey, String bearerToken, String modelId) throws Exception {
        System.out.print("  测试非流式请求... ");
        
        OpenAI client = OpenAI.bedrock(region, bearerKey, bearerToken, modelId);
        
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("Say 'test'"));
        request.setMessages(messages);
        
        ChatCompletionResponse response = client.createChatCompletion(request);
        System.out.println("成功 - 响应: " + response.getContent());
    }
    
    // 测试流式请求
    private static void testStreaming(String region, String bearerKey, String bearerToken, String modelId) throws Exception {
        System.out.print("  测试流式请求... ");
        
        OpenAI client = OpenAI.bedrock(region, bearerKey, bearerToken, modelId);
        
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("Say 'stream test'"));
        request.setMessages(messages);
        
        request.setStream(true);
        
        StringBuilder result = new StringBuilder();
        final boolean[] hasError = {false};
        
        client.createChatCompletionStream(
            request,
            chunk -> {
                String content = chunk.getContent();
                if (content != null) {
                    result.append(content);
                }
            },
            () -> {
                // 完成
            },
            error -> {
                hasError[0] = true;
                throw new RuntimeException(error.getMessage());
            }
        );
        
        // 等待响应
        Thread.sleep(2000);
        
        if (!hasError[0] && result.length() > 0) {
            System.out.println("成功 - 响应: " + result.toString());
        } else {
            throw new RuntimeException("流式请求失败");
        }
    }
}