package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 演示如何使用不同的Bedrock模型
 */
public class BedrockMultiModelDemo {
    
    public static void main(String[] args) throws Exception {
        // 从环境变量获取凭证
        String bearerKey = System.getenv("AWS_BEARER_KEY_BEDROCK");
        String bearerToken = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
        
        if (bearerKey == null || bearerToken == null) {
            System.err.println("请设置环境变量 AWS_BEARER_KEY_BEDROCK 和 AWS_BEARER_TOKEN_BEDROCK");
            System.exit(1);
        }
        
        // 定义要测试的模型
        String[] modelIds = {
            "us.anthropic.claude-3-7-sonnet-20250219-v1:0",  // Claude
            "amazon.titan-text-express-v1",                   // Titan
            "cohere.command-text-v14",                        // Cohere Command
            "ai21.j2-mid-v1"                                  // Jurassic-2 Mid
        };
        
        String region = "us-east-2";
        
        for (String modelId : modelIds) {
            System.out.println("\n=== 测试模型: " + modelId + " ===");
            
            try {
                // 创建客户端
                OpenAI client = OpenAI.bedrock(region, bearerKey, bearerToken, modelId);
                
                // 创建请求
                List<ChatMessage> messages = new ArrayList<>();
                messages.add(ChatMessage.user("请用一句话介绍你自己"));
                
                ChatCompletionRequest request = new ChatCompletionRequest();
                request.setModel(modelId);
                request.setMessages(messages);
                request.setMaxTokens(100);
                
                // 清除不必要的默认值
                request.setTemperature(null);
                request.setTopP(null);
                request.setN(null);
                request.setPresencePenalty(null);
                request.setFrequencyPenalty(null);
                request.setLogprobs(null);
                request.setServiceTier(null);
                
                // 发送请求
                long startTime = System.currentTimeMillis();
                ChatCompletionResponse response = client.createChatCompletion(request);
                long endTime = System.currentTimeMillis();
                
                // 输出结果
                System.out.println("响应: " + response.getContent());
                System.out.println("耗时: " + (endTime - startTime) + "ms");
                
                // 输出usage信息
                if (response.getUsage() != null) {
                    System.out.println("Usage:");
                    System.out.println("  输入tokens: " + response.getUsage().getPromptTokens());
                    System.out.println("  输出tokens: " + response.getUsage().getCompletionTokens());
                    System.out.println("  总tokens: " + response.getUsage().getTotalTokens());
                }
                
                // 测试流式响应（如果支持）
                testStreaming(client, modelId, messages);
                
            } catch (Exception e) {
                System.err.println("错误: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private static void testStreaming(OpenAI client, String modelId, List<ChatMessage> messages) {
        // 检查模型是否支持流式响应
        if (modelId.contains("ai21.j2") || modelId.contains("meta.llama")) {
            System.out.println("该模型不支持流式响应");
            return;
        }
        
        System.out.println("\n测试流式响应...");
        
        try {
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel(modelId);
            request.setMessages(messages);
            request.setMaxTokens(100);
            request.setStream(true);
            
            // 设置stream_options
            ChatCompletionRequest.StreamOptions streamOptions = new ChatCompletionRequest.StreamOptions(true);
            request.setStreamOptions(streamOptions);
            
            // 清除默认值
            request.setTemperature(null);
            request.setTopP(null);
            request.setN(null);
            request.setPresencePenalty(null);
            request.setFrequencyPenalty(null);
            request.setLogprobs(null);
            request.setServiceTier(null);
            
            StringBuilder content = new StringBuilder();
            final ChatCompletionResponse.Usage[] finalUsage = {null};
            
            client.createChatCompletionStream(
                request,
                chunk -> {
                    String chunkContent = chunk.getContent();
                    if (chunkContent != null && !chunkContent.isEmpty()) {
                        content.append(chunkContent);
                    }
                    if (chunk.getUsage() != null) {
                        finalUsage[0] = chunk.getUsage();
                    }
                },
                () -> {
                    System.out.println("流式响应: " + content.toString());
                    if (finalUsage[0] != null) {
                        System.out.println("流式Usage:");
                        System.out.println("  输入tokens: " + finalUsage[0].getPromptTokens());
                        System.out.println("  输出tokens: " + finalUsage[0].getCompletionTokens());
                        System.out.println("  总tokens: " + finalUsage[0].getTotalTokens());
                    }
                },
                error -> {
                    System.err.println("流式错误: " + error.getMessage());
                }
            );
            
            // 等待流式响应完成
            Thread.sleep(3000);
            
        } catch (Exception e) {
            System.err.println("流式测试失败: " + e.getMessage());
        }
    }
}