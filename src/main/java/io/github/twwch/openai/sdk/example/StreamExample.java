package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/**
 * 流式输出示例
 */
public class StreamExample {
    public static void main(String[] args) {
//        String apiKey = "";
//        String baseUrl = "";
//        String model = "";
        // 创建OpenAI客户端
        String apiKey = "";

        // 替换为你的Azure OpenAI资源名称
        String resourceName = "iweaver-ai2";

        // 替换为你的Azure OpenAI部署ID
        String deploymentId = "gpt-4.1";

        // 创建Azure OpenAI客户端
        OpenAI openai = OpenAI.azure(apiKey, resourceName, deploymentId);
//        OpenAI openai = new OpenAI(apiKey, baseUrl);
        
        System.out.println("=== 流式聊天示例 1：简单对话 ===");
        advancedStreamChat(openai, deploymentId);
        
//        System.out.println("\n=== 流式聊天示例 2：带系统提示 ===");
//        streamChatWithSystemPrompt(openai, deploymentId);
//
//        System.out.println("\n=== 流式聊天示例 3：完整控制 ===");
//        advancedStreamChat(openai, deploymentId);
        
        // 强制退出，避免OkHttp线程池导致的延迟
        System.exit(0);
    }
    
    /**
     * 简单的流式对话
     */
    private static void simpleStreamChat(OpenAI openai, String model) {
        try {
            System.out.print("AI: ");
            
            // 最简单的流式调用
            openai.chatStream(model, "给我讲一个简短的笑话",
                content -> System.out.print(content)
            );
            
            System.out.println("\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 带系统提示的流式对话
     */
    private static void streamChatWithSystemPrompt(OpenAI openai,  String model) {
        try {
            System.out.print("AI: ");
            
            openai.chatStream(
                model,
                "你是一个诗人，用优美的语言回答问题",
                "描述一下春天的景色",
                content -> System.out.print(content)
            );
            
            System.out.println("\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 高级流式对话（完整控制）
     */
    private static void advancedStreamChat(OpenAI openai,   String model) {
        try {
            // 使用 CountDownLatch 等待流式响应完成
            CountDownLatch latch = new CountDownLatch(1);
            StringBuilder fullResponse = new StringBuilder();
            
            // 创建请求
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setStreamOptions(new ChatCompletionRequest.StreamOptions(true));
            request.setModel(model);
            request.setMessages(Arrays.asList(
                ChatMessage.system("你是一个有帮助的助手"),
                ChatMessage.user("你好")
            ));
            request.setTemperature(0.7);
            request.setMaxTokens(200);
            
            System.out.print("AI: ");
            
            // 执行流式请求
            openai.createChatCompletionStream(
                request,
                // 处理每个数据块
                chunk -> {
                    String content = chunk.getContent();
                    if (content != null) {
                        System.out.print(content);
                        fullResponse.append(content);
                    }
                    
                    // 检查是否有 finish_reason
                    if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                        String finishReason = chunk.getChoices().get(0).getFinishReason();
                        if (finishReason != null) {
                            System.out.println("\n[完成原因: " + finishReason + "]");
                        }
                    }
                    
                    // 检查是否有 usage 信息
                    if (chunk.getUsage() != null) {
                        System.out.println("\n[Token使用统计:]");
                        System.out.println("  - 输入Tokens: " + chunk.getUsage().getPromptTokens());
                        System.out.println("  - 输出Tokens: " + chunk.getUsage().getCompletionTokens());
                        System.out.println("  - 总计Tokens: " + chunk.getUsage().getTotalTokens());
                    }
                },
                // 完成时的回调
                () -> {
                    System.out.println("\n[流式响应完成]");
                    latch.countDown();
                },
                // 错误时的回调
                error -> {
                    System.err.println("\n[错误: " + error.getMessage() + "]");
                    error.printStackTrace();
                    latch.countDown();
                }
            );
            
            // 等待完成
            latch.await();
            
            System.out.println("\n完整响应长度: " + fullResponse.length() + " 字符");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}