package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class OptimizedStreamingTest {
    
    public static void main(String[] args) throws Exception {
        // 测试单个请求，避免并发导致的性能问题
        testSingleStreamingRequest();
        
        // 等待一段时间确保所有资源释放
        Thread.sleep(1000);
        
        // 测试多个顺序请求
        System.out.println("\n=== 顺序测试3个请求 ===");
        for (int i = 0; i < 3; i++) {
            System.out.println("\n--- 请求 " + (i + 1) + " ---");
            testSingleStreamingRequest();
            Thread.sleep(500); // 请求之间稍微等待
        }
        
        System.out.println("\n所有测试完成");
        
        // 不需要System.exit(0)，程序应该能正常退出
    }
    
    private static void testSingleStreamingRequest() throws Exception {
        // 创建Azure客户端
        AzureOpenAIConfig azureConfig = new AzureOpenAIConfig(
                System.getenv("AZURE_TOKEN"),
            "iweaver-ai2", 
            "gpt-4o-mini",
            "2025-04-01-preview"
        );
        OpenAI client = new OpenAI(azureConfig);
        
        // 创建最简单的请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(Collections.singletonList(
            ChatMessage.user("Hello")
        ));
        request.setStream(true);
        
        // 移除所有非必要参数
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogprobs(null);
        request.setServiceTier(null);
        request.setStreamOptions(null);
        
        // 计时变量
        CountDownLatch latch = new CountDownLatch(1);
        long[] timestamps = new long[5]; // 记录多个时间点
        int[] tokenCount = {0};
        boolean[] firstChunkReceived = {false};
        
        // 记录发送请求前的时间
        timestamps[0] = System.currentTimeMillis();
        System.out.println("T0 - 准备发送请求: " + timestamps[0]);
        
        // 发送流式请求
        client.createChatCompletionStream(
            request,
            chunk -> {
                long now = System.currentTimeMillis();
                
                // 记录第一个chunk到达时间
                if (!firstChunkReceived[0]) {
                    firstChunkReceived[0] = true;
                    timestamps[1] = now;
                    System.out.println("T1 - 第一个chunk到达: " + timestamps[1] + " (延迟: " + (timestamps[1] - timestamps[0]) + "ms)");
                }
                
                String content = chunk.getContent();
                if (content != null) {
                    tokenCount[0]++;
                    
                    // 记录第一个有内容的token
                    if (timestamps[2] == 0) {
                        timestamps[2] = now;
                        System.out.println("T2 - 第一个token: " + timestamps[2] + " (延迟: " + (timestamps[2] - timestamps[0]) + "ms), 内容: \"" + content + "\"");
                    }
                    // 记录第二个有内容的token
                    else if (timestamps[3] == 0) {
                        timestamps[3] = now;
                        System.out.println("T3 - 第二个token: " + timestamps[3] + " (延迟: " + (timestamps[3] - timestamps[0]) + "ms), 内容: \"" + content + "\"");
                    }
                }
            },
            () -> {
                timestamps[4] = System.currentTimeMillis();
                System.out.println("T4 - 流完成: " + timestamps[4] + " (总耗时: " + (timestamps[4] - timestamps[0]) + "ms)");
                latch.countDown();
            },
            error -> {
                System.err.println("错误: " + error.getMessage());
                error.printStackTrace();
                latch.countDown();
            }
        );
        
        // 等待完成，设置超时避免无限等待
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        if (!completed) {
            System.err.println("警告：请求超时（30秒）");
        }
        
        // 打印统计
        System.out.println("\n统计信息:");
        System.out.println("  总token数: " + tokenCount[0]);
        if (timestamps[2] > 0) {
            System.out.println("  首token延迟: " + (timestamps[2] - timestamps[0]) + "ms");
        }
        if (timestamps[3] > 0) {
            System.out.println("  第二token延迟: " + (timestamps[3] - timestamps[0]) + "ms");
        }
    }
}