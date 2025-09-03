package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 简单的Bedrock连接测试
 * 用于快速验证连接池问题是否修复
 */
public class SimpleBedrockTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 简化的Bedrock连接池测试 ===\n");

        // 配置
        String region = System.getProperty("bedrock.region", "us-west-2");
        String accessKeyId = System.getProperty("bedrock.accessKeyId", System.getenv("AWS_ACCESS_KEY_ID"));
        String secretAccessKey = System.getProperty("bedrock.secretAccessKey", System.getenv("AWS_SECRET_ACCESS_KEY"));
        String modelId = System.getProperty("bedrock.modelId", "us.anthropic.claude-3-haiku-20240307");

        if (accessKeyId == null || secretAccessKey == null) {
            System.err.println("请设置AWS凭证");
            System.exit(1);
        }

        // 创建服务
        OpenAI service = OpenAI.bedrock(region, accessKeyId, secretAccessKey, modelId);
        System.out.println("使用模型: " + modelId);
        System.out.println("区域: " + region + "\n");

        // 测试10个并发请求
        int numRequests = 10;
        CountDownLatch latch = new CountDownLatch(numRequests);
        
        System.out.println("发送 " + numRequests + " 个并发流式请求...\n");
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 1; i <= numRequests; i++) {
            final int requestId = i;
            
            // 创建请求
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel(modelId);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("你是一个助手"));
            messages.add(ChatMessage.user("用10个字介绍自己"));
            request.setMessages(messages);
            
            // 发送异步请求
            new Thread(() -> {
                try {
                    CountDownLatch streamLatch = new CountDownLatch(1);
                    StringBuilder response = new StringBuilder();
                    
                    long requestStart = System.currentTimeMillis();
                    
                    service.createChatCompletionStream(request,
                        chunk -> {
                            if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                                ChatCompletionChunk.Choice choice = chunk.getChoices().get(0);
                                if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                                    response.append(choice.getDelta().getContent());
                                }
                            }
                        },
                        () -> {
                            long elapsed = System.currentTimeMillis() - requestStart;
                            System.out.printf("[请求 #%d] ✓ 完成 (%d ms): %s\n", 
                                requestId, elapsed, response.toString().replace("\n", " "));
                            streamLatch.countDown();
                        },
                        e -> {
                            System.err.printf("[请求 #%d] ✗ 错误: %s\n", requestId, e.getMessage());
                            streamLatch.countDown();
                        }
                    );
                    
                    // 等待完成（最多60秒）
                    if (!streamLatch.await(60, TimeUnit.SECONDS)) {
                        System.err.printf("[请求 #%d] ✗ 超时\n", requestId);
                    }
                    
                } catch (Exception e) {
                    System.err.printf("[请求 #%d] ✗ 异常: %s\n", requestId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
            
            // 稍微延迟，避免瞬间发送太多请求
            Thread.sleep(100);
        }
        
        // 等待所有请求完成
        if (latch.await(90, TimeUnit.SECONDS)) {
            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("\n=============================");
            System.out.println("✅ 所有请求成功完成!");
            System.out.printf("总耗时: %.1f 秒\n", totalTime / 1000.0);
            System.out.println("=============================");
        } else {
            System.err.println("\n❌ 部分请求未完成（超时）");
        }
    }
}