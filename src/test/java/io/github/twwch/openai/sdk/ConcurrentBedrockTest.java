package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bedrock并发测试
 * 模拟30个并发请求，测试连接池是否会耗尽
 */
public class ConcurrentBedrockTest {

    // 测试配置
    private static final int CONCURRENT_REQUESTS = 30;  // 并发请求数
    private static final int TOTAL_REQUESTS = 100;      // 总请求数
    private static final boolean USE_STREAMING = true;  // 是否使用流式响应
    private static final int STREAM_TIMEOUT_SECONDS = 90; // 流式响应超时时间（秒）

    // 统计信息
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);
    private static final AtomicInteger connectionPoolErrorCount = new AtomicInteger(0);
    private static final AtomicInteger activeConnections = new AtomicInteger(0);
    private static final AtomicInteger peakConnections = new AtomicInteger(0);
    private static final AtomicInteger streamNotCompletedCount = new AtomicInteger(0);
    private static final AtomicLong totalResponseTime = new AtomicLong(0);
    private static final ConcurrentHashMap<String, Integer> errorMessages = new ConcurrentHashMap<>();
    private static final List<Long> connectionHoldTimes = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        System.out.println("=== Bedrock 并发连接池测试 ===");
        System.out.println("并发请求数: " + CONCURRENT_REQUESTS);
        System.out.println("总请求数: " + TOTAL_REQUESTS);
        System.out.println("使用流式响应: " + USE_STREAMING);
        System.out.println("=============================\n");

        // 从环境变量或系统属性获取配置
        String region = System.getProperty("bedrock.region", "us-west-2");
        String accessKeyId = System.getProperty("bedrock.accessKeyId", System.getenv("AWS_ACCESS_KEY_ID"));
        String secretAccessKey = System.getProperty("bedrock.secretAccessKey", System.getenv("AWS_SECRET_ACCESS_KEY"));
        String modelId = System.getProperty("bedrock.modelId", "us.anthropic.claude-3-7-sonnet-20250219-v1:0");

        if (accessKeyId == null || secretAccessKey == null) {
            System.err.println("请设置AWS凭证：");
            System.err.println("  通过系统属性: -Dbedrock.accessKeyId=xxx -Dbedrock.secretAccessKey=xxx");
            System.err.println("  或通过环境变量: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY");
            System.exit(1);
        }

        // 显示配置信息
        System.out.println("配置信息:");
        System.out.println("使用模型: " + modelId);
        System.out.println("区域: " + region);
        System.out.println("每次请求都会重新创建 OpenAI service 实例\n");

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);  // 确保所有线程同时开始
        CountDownLatch completeLatch = new CountDownLatch(TOTAL_REQUESTS);

        // 提交任务
        System.out.println("准备发送 " + TOTAL_REQUESTS + " 个请求...\n");
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int requestId = i + 1;
            Future<?> future = executor.submit(() -> {
                try {
                    // 等待开始信号
                    startLatch.await();
                    
                    // 添加小延迟，避免瞬间发送太多请求
                    if (requestId > CONCURRENT_REQUESTS) {
                        Thread.sleep((requestId / CONCURRENT_REQUESTS) * 200L);
                    }
                    
                    // 执行请求（每次创建新的 service 实例）
                    sendRequest(region, accessKeyId, secretAccessKey, modelId, requestId);
                    
                } catch (Exception e) {
                    System.err.println("[请求 #" + requestId + "] 意外错误: " + e.getMessage());
                    failureCount.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            });
            futures.add(future);
        }

        // 开始测试
        long startTime = System.currentTimeMillis();
        System.out.println("开始发送请求...\n");
        startLatch.countDown();  // 释放所有线程

        // 等待所有请求完成（最多等待10分钟）
        boolean completed = completeLatch.await(10, TimeUnit.MINUTES);
        long endTime = System.currentTimeMillis();

        if (!completed) {
            System.err.println("\n警告：部分请求未在10分钟内完成！");
            // 取消未完成的任务
            for (Future<?> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
        }

        // 关闭线程池
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // 打印统计结果
        printStatistics(startTime, endTime);
    }

    private static void sendRequest(String region, String accessKeyId, String secretAccessKey, String modelId, int requestId) {
        long requestStartTime = System.currentTimeMillis();
        
        try (// 使用 try-with-resources 确保资源释放
             OpenAI service = OpenAI.bedrock(region, accessKeyId, secretAccessKey, modelId)) {
            // 构建请求
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel(modelId);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("你是一个 helpful 的助手"));
            messages.add(ChatMessage.user("写一个 1000字左右的故事"));
            request.setMessages(
                    messages
            );

            if (USE_STREAMING) {
                // 流式请求
                CountDownLatch streamLatch = new CountDownLatch(1);
                StringBuilder response = new StringBuilder();
                AtomicReference<Throwable> error = new AtomicReference<>();
                AtomicBoolean streamCompleted = new AtomicBoolean(false);
                
                // 记录连接开始时间
                long connectionStartTime = System.currentTimeMillis();
                int currentActive = activeConnections.incrementAndGet();
                updatePeakConnections(currentActive);
                
                System.out.printf("[请求 #%d] 开始 (活跃连接: %d)\n", requestId, currentActive);

                // 创建流式请求
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
                            // 流完成回调
                            streamCompleted.set(true);
                            long responseTime = System.currentTimeMillis() - requestStartTime;
                            long connectionHoldTime = System.currentTimeMillis() - connectionStartTime;
                            connectionHoldTimes.add(connectionHoldTime);
                            
                            int remainingActive = activeConnections.decrementAndGet();
                            totalResponseTime.addAndGet(responseTime);
                            successCount.incrementAndGet();
                            
                            System.out.printf("[请求 #%d] ✓ 成功 (耗时: %dms, 连接保持: %dms, 剩余活跃: %d) - 响应: %s\n", 
                                requestId, responseTime, connectionHoldTime, remainingActive,
                                response.toString().replace("\n", " ").substring(0, Math.min(response.length(), 50)));
                            streamLatch.countDown();
                        },
                        e -> {
                            // 错误回调
                            error.set(e);
                            long connectionHoldTime = System.currentTimeMillis() - connectionStartTime;
                            connectionHoldTimes.add(connectionHoldTime);
                            int remainingActive = activeConnections.decrementAndGet();
                            
                            handleError(requestId, e, requestStartTime, remainingActive);
                            streamLatch.countDown();
                        }
                );

                // 等待流完成
                boolean completed = false;
                try {
                    completed = streamLatch.await(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    
                    if (!completed) {
                        System.err.printf("[请求 #%d] ✗ 超时 - 流式响应未在%d秒内完成\n", requestId, STREAM_TIMEOUT_SECONDS);
                        failureCount.incrementAndGet();
                        streamNotCompletedCount.incrementAndGet();
                    }
                    
                } finally {
                    // 确保连接计数正确递减
                    if (!streamCompleted.get() && !completed) {
                        int remainingActive = activeConnections.decrementAndGet();
                        System.err.printf("[请求 #%d] 强制释放连接 (剩余活跃: %d)\n", requestId, remainingActive);
                    }
                }
                
            } else {
                // 非流式请求
                int currentActive = activeConnections.incrementAndGet();
                updatePeakConnections(currentActive);
                
                try {
                    ChatCompletionResponse response = service.createChatCompletion(request);
                    
                    long responseTime = System.currentTimeMillis() - requestStartTime;
                    totalResponseTime.addAndGet(responseTime);
                    successCount.incrementAndGet();
                    
                    String content = response.getChoices().get(0).getMessage().getContentAsString();
                    int remainingActive = activeConnections.decrementAndGet();
                    
                    System.out.printf("[请求 #%d] ✓ 成功 (耗时: %dms, 剩余活跃: %d) - 响应: %s\n", 
                        requestId, responseTime, remainingActive,
                        content.replace("\n", " ").substring(0, Math.min(content.length(), 50)));
                } finally {
                    activeConnections.decrementAndGet();
                }
            }
            
        } catch (Exception e) {
            handleError(requestId, e, requestStartTime, activeConnections.get());
        }
    }

    private static void handleError(int requestId, Throwable e, long startTime, int remainingActive) {
        long responseTime = System.currentTimeMillis() - startTime;
        failureCount.incrementAndGet();
        
        String errorMsg = e.getMessage();
        if (errorMsg != null && (errorMsg.contains("Acquire operation took longer than") || 
                                 errorMsg.contains("connection pool") || 
                                 errorMsg.contains("ClosedChannelException"))) {
            connectionPoolErrorCount.incrementAndGet();
            System.err.printf("[请求 #%d] ✗ 连接池错误 (耗时: %dms, 剩余活跃: %d) - %s\n", 
                requestId, responseTime, remainingActive, errorMsg);
        } else {
            System.err.printf("[请求 #%d] ✗ 失败 (耗时: %dms, 剩余活跃: %d) - %s\n", 
                requestId, responseTime, remainingActive, errorMsg);
        }
        
        // 统计错误类型
        errorMessages.merge(e.getClass().getSimpleName(), 1, Integer::sum);
    }
    
    private static void updatePeakConnections(int current) {
        int peak = peakConnections.get();
        while (current > peak) {
            if (peakConnections.compareAndSet(peak, current)) {
                break;
            }
            peak = peakConnections.get();
        }
    }

    private static void printStatistics(long startTime, long endTime) {
        System.out.println("\n=============================");
        System.out.println("=== 测试结果统计 ===");
        System.out.println("=============================");
        
        int total = successCount.get() + failureCount.get();
        double successRate = total > 0 ? (successCount.get() * 100.0 / total) : 0;
        long totalTime = endTime - startTime;
        double avgResponseTime = successCount.get() > 0 ? 
            (totalResponseTime.get() / (double) successCount.get()) : 0;
        double throughput = total > 0 ? (total * 1000.0 / totalTime) : 0;
        
        System.out.printf("总请求数: %d\n", TOTAL_REQUESTS);
        System.out.printf("完成请求: %d\n", total);
        System.out.printf("成功: %d (%.1f%%)\n", successCount.get(), successRate);
        System.out.printf("失败: %d\n", failureCount.get());
        System.out.printf("连接池错误: %d\n", connectionPoolErrorCount.get());
        System.out.printf("流未完成数: %d\n", streamNotCompletedCount.get());
        System.out.println();
        
        System.out.println("连接池统计:");
        System.out.printf("峰值并发连接: %d\n", peakConnections.get());
        System.out.printf("最终活跃连接: %d\n", activeConnections.get());
        
        if (!connectionHoldTimes.isEmpty()) {
            long avgHoldTime = connectionHoldTimes.stream()
                .mapToLong(Long::longValue)
                .sum() / connectionHoldTimes.size();
            System.out.printf("平均连接保持时间: %d ms\n", avgHoldTime);
        }
        System.out.println();
        
        System.out.printf("总耗时: %.1f 秒\n", totalTime / 1000.0);
        System.out.printf("平均响应时间: %.0f ms\n", avgResponseTime);
        System.out.printf("吞吐量: %.1f 请求/秒\n", throughput);
        
        if (!errorMessages.isEmpty()) {
            System.out.println("\n错误类型统计:");
            errorMessages.forEach((error, count) -> 
                System.out.printf("  %s: %d 次\n", error, count));
        }
        
        System.out.println("\n=============================");
        
        // 判断测试是否通过
        boolean hasConnectionLeak = activeConnections.get() > 0;
        boolean hasPoolErrors = connectionPoolErrorCount.get() > 0;
        boolean hasHighFailureRate = failureCount.get() > TOTAL_REQUESTS * 0.1;
        boolean hasIncompleteStreams = streamNotCompletedCount.get() > 0;
        
        if (hasConnectionLeak) {
            System.err.println("❌ 测试失败: 检测到连接泄漏!");
            System.err.println("   仍有 " + activeConnections.get() + " 个连接未释放。");
        }
        
        if (hasPoolErrors) {
            System.err.println("❌ 测试失败: 出现了 " + connectionPoolErrorCount.get() + " 次连接池错误!");
            System.err.println("   请检查连接池配置和流式响应的资源释放。");
        }
        
        if (hasIncompleteStreams) {
            System.err.println("⚠️  测试警告: " + streamNotCompletedCount.get() + " 个流式请求未正常完成");
        }
        
        if (hasHighFailureRate) {
            System.err.println("⚠️  测试警告: 失败率较高 (" + String.format("%.1f%%", 100 - successRate) + ")");
        }
        
        if (!hasConnectionLeak && !hasPoolErrors && !hasHighFailureRate) {
            System.out.println("✅ 测试通过: 连接池管理正常，没有资源泄漏!");
        }
    }
}