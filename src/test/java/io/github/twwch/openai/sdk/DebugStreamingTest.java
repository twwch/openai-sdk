package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;

public class DebugStreamingTest {
    
    public static void main(String[] args) throws Exception {
        // 测试1：单个请求的性能
        System.out.println("=== 测试1：单个请求性能 ===");
        testSingleRequest();
        
        Thread.sleep(2000);
        
        // 测试2：连续3个请求的性能
        System.out.println("\n\n=== 测试2：连续3个请求 ===");
        for (int i = 0; i < 3; i++) {
            System.out.println("\n请求 " + (i + 1) + ":");
            testSingleRequest();
            Thread.sleep(1000); // 等待1秒再发下一个请求
        }
        
        Thread.sleep(2000);
        
        // 测试3：并发3个请求
        System.out.println("\n\n=== 测试3：并发3个请求 ===");
        CountDownLatch concurrentLatch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            final int requestId = i + 1;
            new Thread(() -> {
                try {
                    System.out.println("\n[线程" + requestId + "] 开始");
                    testSingleRequest();
                    System.out.println("[线程" + requestId + "] 完成");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    concurrentLatch.countDown();
                }
            }).start();
        }
        concurrentLatch.await();
        
        System.out.println("\n\n所有测试完成");
    }
    
    private static void testSingleRequest() throws Exception {
        // 使用硬编码的token
        AzureOpenAIConfig azureConfig = new AzureOpenAIConfig(
            "1",
            "iweaver-ai2",
            "gpt-4o-mini",
            "2025-04-01-preview"
        );
        OpenAI client = new OpenAI(azureConfig);
        
        // 创建请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(Collections.singletonList(
            ChatMessage.user("Hello")
        ));
        request.setStream(true);
        
        // 清除所有默认值
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogprobs(null);
        request.setServiceTier(null);
        request.setStreamOptions(null);
        
        // 打印请求JSON
        ObjectMapper mapper = new ObjectMapper();
        System.out.println("请求JSON: " + mapper.writeValueAsString(request));
        
        // 时间统计
        CountDownLatch latch = new CountDownLatch(1);
        long[] firstTokenTime = {-1};
        boolean[] hasReceivedChunk = {false};
        
        System.out.println("发送请求前时间戳: " + System.currentTimeMillis());
        long startTime = System.currentTimeMillis();
        
        // 发送请求
        client.createChatCompletionStream(
            request,
            chunk -> {
                if (!hasReceivedChunk[0]) {
                    hasReceivedChunk[0] = true;
                    System.out.println("收到第一个chunk时间戳: " + System.currentTimeMillis());
                }
                
                String content = chunk.getContent();
                if (content != null && firstTokenTime[0] == -1) {
                    firstTokenTime[0] = System.currentTimeMillis();
                    long latency = firstTokenTime[0] - startTime;
                    System.out.println("首个token延迟: " + latency + "ms, 内容: \"" + content + "\"");
                }
            },
            () -> {
                long endTime = System.currentTimeMillis();
                System.out.println("流完成，总耗时: " + (endTime - startTime) + "ms");
                latch.countDown();
            },
            error -> {
                System.err.println("错误: " + error.getMessage());
                error.printStackTrace();
                latch.countDown();
            }
        );
        
        System.out.println("请求已发送，等待响应...");
        
        // 等待完成
        latch.await();
    }
}