package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class ConcurrencyDiagnosticTest {
    
    public static void main(String[] args) throws Exception {
        // 创建Azure客户端
        String azureToken = System.getenv("AZURE_TOKEN");
        if (azureToken == null || azureToken.isEmpty()) {
            azureToken = "1";
        }
        
        AzureOpenAIConfig azureConfig = new AzureOpenAIConfig(
            azureToken,
            "iweaver-ai2",
            "gpt-4o-mini",
            "2025-04-01-preview"
        );
        OpenAI client = new OpenAI(azureConfig);
        
        // 测试参数
        int requestCount = 10;
        String testQuestion = "Hello";
        
        System.out.println("=== 并发性能诊断测试 ===");
        System.out.println("模型: gpt-4o-mini");
        System.out.println("请求数: " + requestCount);
        System.out.println("问题: " + testQuestion);
        System.out.println();
        
        // 1. 顺序执行测试
        System.out.println("1. 顺序执行测试");
        System.out.println("-".repeat(50));
        List<Long> sequentialLatencies = testSequential(client, testQuestion, requestCount);
        printStats("顺序执行", sequentialLatencies);
        
        Thread.sleep(2000);
        
        // 2. 并发执行测试（使用固定线程池）
        System.out.println("\n2. 并发执行测试（固定线程池，5个线程）");
        System.out.println("-".repeat(50));
        List<Long> concurrentFixedLatencies = testConcurrentFixed(client, testQuestion, requestCount, 5);
        printStats("并发-固定线程池", concurrentFixedLatencies);
        
        Thread.sleep(2000);
        
        // 3. 并发执行测试（使用缓存线程池）
        System.out.println("\n3. 并发执行测试（缓存线程池）");
        System.out.println("-".repeat(50));
        List<Long> concurrentCachedLatencies = testConcurrentCached(client, testQuestion, requestCount);
        printStats("并发-缓存线程池", concurrentCachedLatencies);
        
        Thread.sleep(2000);
        
        // 4. 并发执行测试（限流版本）
        System.out.println("\n4. 并发执行测试（限流，最多3个并发）");
        System.out.println("-".repeat(50));
        List<Long> concurrentThrottledLatencies = testConcurrentThrottled(client, testQuestion, requestCount, 3);
        printStats("并发-限流", concurrentThrottledLatencies);
        
        // 5. 分析线程池状态
        System.out.println("\n5. 线程池诊断");
        System.out.println("-".repeat(50));
        diagnoseThreadPool(client, testQuestion);
        
        System.out.println("\n测试完成");
    }
    
    // 顺序执行
    private static List<Long> testSequential(OpenAI client, String question, int count) throws Exception {
        List<Long> latencies = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            long latency = measureSingleRequest(client, question, "Sequential-" + (i+1));
            latencies.add(latency);
            Thread.sleep(100); // 请求间隔
        }
        
        return latencies;
    }
    
    // 并发执行 - 固定线程池
    private static List<Long> testConcurrentFixed(OpenAI client, String question, int count, int threadCount) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Long>> futures = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        
        // 提交所有任务
        for (int i = 0; i < count; i++) {
            final int taskId = i + 1;
            Future<Long> future = executor.submit(() -> 
                measureSingleRequest(client, question, "ConcurrentFixed-" + taskId)
            );
            futures.add(future);
        }
        
        // 收集结果
        for (Future<Long> future : futures) {
            latencies.add(future.get());
        }
        
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        
        return latencies;
    }
    
    // 并发执行 - 缓存线程池
    private static List<Long> testConcurrentCached(OpenAI client, String question, int count) throws Exception {
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<Long>> futures = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        
        // 提交所有任务
        for (int i = 0; i < count; i++) {
            final int taskId = i + 1;
            Future<Long> future = executor.submit(() -> 
                measureSingleRequest(client, question, "ConcurrentCached-" + taskId)
            );
            futures.add(future);
        }
        
        // 收集结果
        for (Future<Long> future : futures) {
            latencies.add(future.get());
        }
        
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        
        return latencies;
    }
    
    // 并发执行 - 限流版本
    private static List<Long> testConcurrentThrottled(OpenAI client, String question, int count, int maxConcurrent) throws Exception {
        ExecutorService executor = Executors.newCachedThreadPool();
        Semaphore semaphore = new Semaphore(maxConcurrent);
        List<Future<Long>> futures = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        
        // 提交所有任务
        for (int i = 0; i < count; i++) {
            final int taskId = i + 1;
            Future<Long> future = executor.submit(() -> {
                try {
                    semaphore.acquire();
                    try {
                        return measureSingleRequest(client, question, "ConcurrentThrottled-" + taskId);
                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1L;
                }
            });
            futures.add(future);
        }
        
        // 收集结果
        for (Future<Long> future : futures) {
            latencies.add(future.get());
        }
        
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        
        return latencies;
    }
    
    // 测量单个请求
    private static long measureSingleRequest(OpenAI client, String question, String requestId) {
        try {
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel("gpt-4o-mini");
            request.setMessages(Collections.singletonList(
                ChatMessage.user(question)
            ));
            request.setStream(true);
            
            // 清除默认值
            request.setTemperature(null);
            request.setTopP(null);
            request.setN(null);
            request.setPresencePenalty(null);
            request.setFrequencyPenalty(null);
            request.setLogprobs(null);
            request.setServiceTier(null);
            request.setStreamOptions(null);
            
            CountDownLatch latch = new CountDownLatch(1);
            AtomicLong firstTokenTime = new AtomicLong(-1);
            
            long startTime = System.currentTimeMillis();
            
            client.createChatCompletionStream(
                request,
                chunk -> {
                    if (chunk.getContent() != null && firstTokenTime.get() == -1) {
                        firstTokenTime.set(System.currentTimeMillis());
                    }
                },
                latch::countDown,
                error -> {
                    System.err.println("[" + requestId + "] 错误: " + error.getMessage());
                    latch.countDown();
                }
            );
            
            latch.await();
            
            long latency = firstTokenTime.get() - startTime;
            System.out.println("[" + requestId + "] 首token延迟: " + latency + "ms");
            return latency;
            
        } catch (Exception e) {
            System.err.println("[" + requestId + "] 异常: " + e.getMessage());
            return -1;
        }
    }
    
    // 诊断线程池状态
    private static void diagnoseThreadPool(OpenAI client, String question) throws Exception {
        System.out.println("创建10个并发请求，观察线程池行为...");
        
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch startLatch = new CountDownLatch(1); // 用于同步启动
        CountDownLatch endLatch = new CountDownLatch(10);
        
        // 记录每个请求的时间线
        Map<Integer, Long> submitTimes = new ConcurrentHashMap<>();
        Map<Integer, Long> startTimes = new ConcurrentHashMap<>();
        Map<Integer, Long> firstTokenTimes = new ConcurrentHashMap<>();
        
        // 提交10个任务
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            submitTimes.put(taskId, System.currentTimeMillis());
            
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    startTimes.put(taskId, System.currentTimeMillis());
                    
                    ChatCompletionRequest request = new ChatCompletionRequest();
                    request.setModel("gpt-4o-mini");
                    request.setMessages(Collections.singletonList(
                        ChatMessage.user(question)
                    ));
                    request.setStream(true);
                    request.setTemperature(null);
                    request.setTopP(null);
                    request.setN(null);
                    request.setPresencePenalty(null);
                    request.setFrequencyPenalty(null);
                    request.setLogprobs(null);
                    request.setServiceTier(null);
                    request.setStreamOptions(null);
                    
                    CountDownLatch requestLatch = new CountDownLatch(1);
                    
                    client.createChatCompletionStream(
                        request,
                        chunk -> {
                            if (chunk.getContent() != null && !firstTokenTimes.containsKey(taskId)) {
                                firstTokenTimes.put(taskId, System.currentTimeMillis());
                            }
                        },
                        requestLatch::countDown,
                        error -> requestLatch.countDown()
                    );
                    
                    requestLatch.await();
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        Thread.sleep(100); // 确保所有任务都已提交
        long baseTime = System.currentTimeMillis();
        startLatch.countDown(); // 统一开始
        
        endLatch.await();
        
        // 分析时间线
        System.out.println("\n时间线分析（相对于统一开始时间）:");
        for (int i = 0; i < 10; i++) {
            long submitDelay = submitTimes.get(i) - baseTime;
            long startDelay = startTimes.get(i) - baseTime;
            long firstTokenDelay = firstTokenTimes.get(i) - startTimes.get(i);
            
            System.out.printf("Task %d: 提交@%dms, 开始@%dms, 首token延迟=%dms\n", 
                i, submitDelay, startDelay, firstTokenDelay);
        }
        
        executor.shutdown();
    }
    
    // 打印统计信息
    private static void printStats(String label, List<Long> latencies) {
        if (latencies.isEmpty()) return;
        
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long min = latencies.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
        
        System.out.printf("%s - 平均: %.2fms, 最小: %dms, 最大: %dms\n", 
            label, avg, min, max);
    }
}