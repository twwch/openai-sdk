package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;

public class SimpleStreamingTest {
    
    public static void main(String[] args) throws Exception {
        // 从环境变量获取Azure Token
        String azureToken = System.getenv("AZURE_TOKEN");
        if (azureToken == null || azureToken.isEmpty()) {
            System.err.println("请设置环境变量 AZURE_TOKEN");
            System.exit(1);
        }
        
        // 创建Azure客户端
        AzureOpenAIConfig azureConfig = new AzureOpenAIConfig(
            azureToken,
            "iweaver-ai2",
            "gpt-4o-mini",  // 直接使用gpt-4o-mini作为部署ID
            "2025-04-01-preview"
        );
        OpenAI client = new OpenAI(azureConfig);
        
        // 测试简单的问题
        String testQuestion = "你好，什么是服务降级";
        System.out.println("测试问题: " + testQuestion);
        System.out.println("=".repeat(50));
        
        // 创建最简单的请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(Collections.singletonList(
            ChatMessage.user(testQuestion)
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
        
        // 时间统计
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder fullResponse = new StringBuilder();
        
        long[] firstTokenTime = {-1};
        long[] secondTokenTime = {-1};
        String[] firstTokenContent = {null};
        String[] secondTokenContent = {null};
        int[] tokenCount = {0};
        
        System.out.println("开始发送请求...");
        long startTime = System.currentTimeMillis();
        
        // 发送流式请求
        client.createChatCompletionStream(
            request,
            chunk -> {
                long currentTime = System.currentTimeMillis();
                String content = chunk.getContent();
                
                if (content != null) {
                    if (firstTokenTime[0] == -1) {
                        firstTokenTime[0] = currentTime;
                        firstTokenContent[0] = content;
                        System.out.println("第一个token到达: " + (currentTime - startTime) + "ms, 内容: \"" + content + "\"");
                    } else if (secondTokenTime[0] == -1) {
                        secondTokenTime[0] = currentTime;
                        secondTokenContent[0] = content;
                        System.out.println("第二个token到达: " + (currentTime - startTime) + "ms, 内容: \"" + content + "\"");
                    }
                    
                    tokenCount[0]++;
                    if (!content.isEmpty()) {
                        fullResponse.append(content);
                        System.out.print(content); // 实时打印内容
                    }
                }
            },
            () -> {
                System.out.println("\n\n流式响应完成");
                latch.countDown();
            },
            error -> {
                System.err.println("错误: " + error.getMessage());
                error.printStackTrace();
                latch.countDown();
            }
        );
        
        // 等待完成
        latch.await();
        
        long endTime = System.currentTimeMillis();
        
        // 打印统计信息
        System.out.println("\n" + "=".repeat(50));
        System.out.println("统计信息:");
        System.out.println("  总耗时: " + (endTime - startTime) + "ms");
        
        if (firstTokenTime[0] != -1) {
            System.out.println("  首token延迟: " + (firstTokenTime[0] - startTime) + "ms");
        }
        
        if (secondTokenTime[0] != -1) {
            System.out.println("  第二token延迟: " + (secondTokenTime[0] - startTime) + "ms");
        }
        
        System.out.println("  总token数: " + tokenCount[0]);
        System.out.println("  响应长度: " + fullResponse.length() + " 字符");
        System.out.println("\n完整响应:");
        System.out.println(fullResponse.toString());

    }
}