package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.AzureOpenAIConfig;
import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.OpenAIConfig;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class StreamingLatencyTest {
    
    // Client configurations
    private static final OpenAI azureClient;
    private static final OpenAI deepseekClient;
    private static final OpenAI claudeClient;
    private static final OpenAI bedrockClient;
    
    static {
        // Initialize Azure client with AzureOpenAIConfig
        String AZURE_TOKEN = System.getenv("AZURE_TOKEN");
        AzureOpenAIConfig azureConfig = new AzureOpenAIConfig(
                AZURE_TOKEN,  // apiKey
            "iweaver-ai2",  // resourceName
            "gpt-4.1",  // deploymentId
            "2025-04-01-preview"  // apiVersion
        );
        azureClient = new OpenAI(azureConfig);
        String deepseekToken = System.getenv("DEEPSEEK_TOKEN");
        
        // Initialize Deepseek client
        deepseekClient = new OpenAI(deepseekToken, "https://ark.cn-beijing.volces.com/api/v3");
        
        // Initialize Claude client
        claudeClient = new OpenAI("1", "https://api.anthropic.com/v1/");
        
        // Initialize Bedrock client with environment variables if available
        String awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
        String awsSecretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        String bearerKey = System.getenv("AWS_BEARER_KEY_BEDROCK");
        String bearerToken = System.getenv("AWS_BEARER_TOKEN_BEDROCK");


        if (awsAccessKeyId != null && awsSecretAccessKey != null) {
            // Use standard AWS credentials
            bedrockClient = OpenAI.bedrock("us-east-2", awsAccessKeyId, awsSecretAccessKey,
                "us.anthropic.claude-3-7-sonnet-20250219-v1:0");
        } else if (bearerKey != null && bearerToken != null) {
            // Use custom bearer credentials
            bedrockClient = OpenAI.bedrock("us-east-2", bearerKey, bearerToken,
                "us.anthropic.claude-3-7-sonnet-20250219-v1:0");
        } else {
            // Use proxy configuration
            OpenAIConfig proxyConfig = new OpenAIConfig(
                "1",  // apiKey
                "https://bedroc-proxy-zxoyif91a9jm-2088680448.us-east-2.elb.amazonaws.com/api/v1"
            );
            bedrockClient = new OpenAI(proxyConfig);
        }
    }
    
    // Sample questions
    private static final List<String> SAMPLE_QUESTIONS = Arrays.asList(
        "你好，什么是服务降级",
        "请解释一下什么是微服务架构",
        "Python中的装饰器是什么",
        "什么是REST API",
        "解释一下TCP和UDP的区别",
        "什么是Docker容器",
        "什么是云计算",
        "解释一下什么是机器学习",
        "什么是版本控制系统",
        "什么是数据库索引",
        "什么是缓存",
        "解释一下什么是负载均衡",
        "什么是分布式系统",
        "什么是消息队列",
        "解释一下什么是OAuth",
        "什么是CDN",
        "什么是反向代理",
        "解释一下什么是JWT",
        "什么是NoSQL数据库",
        "什么是敏捷开发",
        "什么是CI/CD",
        "解释一下什么是Kubernetes",
        "什么是虚拟化技术",
        "什么是API网关",
        "解释一下什么是GraphQL",
        "什么是WebSocket",
        "什么是SSL/TLS",
        "解释一下什么是CORS",
        "什么是数据库事务",
        "什么是设计模式",
        "解释一下什么是MVC架构",
        "什么是单点登录",
        "什么是分布式锁",
        "解释一下什么是服务网格",
        "什么是容器编排",
        "什么是无服务器架构",
        "解释一下什么是区块链",
        "什么是人工智能",
        "什么是深度学习",
        "解释一下什么是自然语言处理"
    );
    
    // Test result class
    static class TestResult {
        String model;
        String sample;
        String firstToken;
        String secondToken;
        Double firstTokenLatencyMs;
        Double secondTokenLatencyMs;
        Integer totalTokens;
        Integer responseLength;
        String fullResponse;
        String error;
        
        // Constructor for successful result
        TestResult(String model, String sample, String firstToken, String secondToken, 
                  Double firstTokenLatencyMs, Double secondTokenLatencyMs, 
                  Integer totalTokens, Integer responseLength, String fullResponse) {
            this.model = model;
            this.sample = sample;
            this.firstToken = firstToken;
            this.secondToken = secondToken;
            this.firstTokenLatencyMs = firstTokenLatencyMs;
            this.secondTokenLatencyMs = secondTokenLatencyMs;
            this.totalTokens = totalTokens;
            this.responseLength = responseLength;
            this.fullResponse = fullResponse;
        }
        
        // Constructor for error result
        TestResult(String model, String sample, String error) {
            this.model = model;
            this.sample = sample;
            this.error = error;
            this.totalTokens = 0;
            this.responseLength = 0;
        }
    }
    
    // Rate limiter for Claude API (3 requests per minute)
    private static final Semaphore claudeSemaphore = new Semaphore(1);
    private static final Queue<Long> claudeRequestTimes = new LinkedList<>();
    private static final Object claudeLock = new Object();
    
    private static void checkClaudeRateLimit() throws InterruptedException {
        synchronized (claudeLock) {
            long currentTime = System.currentTimeMillis();
            // Remove requests older than 1 minute
            claudeRequestTimes.removeIf(time -> currentTime - time > 60000);
            
            if (claudeRequestTimes.size() >= 3) {
                long oldestRequest = claudeRequestTimes.peek();
                long waitTime = 60000 - (currentTime - oldestRequest);
                if (waitTime > 0) {
                    System.out.println("Claude API rate limit, waiting " + waitTime/1000.0 + " seconds...");
                    Thread.sleep(waitTime);
                    // Clean up again after waiting
                    long newCurrentTime = System.currentTimeMillis();
                    claudeRequestTimes.removeIf(time -> newCurrentTime - time > 60000);
                }
            }
            claudeRequestTimes.offer(System.currentTimeMillis());
        }
    }
    
    // Streaming demo with timing statistics
    private static TestResult timingStreamDemo(String modelName, OpenAI client, String sampleQuestion) {
        StringBuilder fullResponse = new StringBuilder();
        
        // Use arrays to allow modification within lambda
        final int[] counters = {0, 0}; // chunkCount, tokenCount
        
        try {
            // 创建一个最小化的请求，只包含必要参数，类似Python版本
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel(modelName);
            request.setMessages(Collections.singletonList(
                ChatMessage.user(sampleQuestion)
            ));
            request.setStream(true);
            
            // 清除所有可能的默认值，只保留必要参数
            request.setTemperature(null);
            request.setTopP(null);
            request.setN(null);
            request.setPresencePenalty(null);
            request.setFrequencyPenalty(null);
            request.setLogprobs(null);
            request.setServiceTier(null);
            request.setStreamOptions(null); // 不要设置stream_options
            
            // 打印请求信息以便调试
            System.out.println("[DEBUG] Model: " + modelName + ", Sample: " + sampleQuestion.substring(0, Math.min(20, sampleQuestion.length())) + "...");
            
            // Use a CountDownLatch to wait for streaming completion
            CountDownLatch latch = new CountDownLatch(1);
            final long[] tokenTimes = {-1, -1}; // firstTokenTime, secondTokenTime
            final String[] tokenContents = {null, null}; // firstTokenContent, secondTokenContent
            final Throwable[] streamError = {null};
            
            // 在实际发送请求之前记录开始时间
            long startTime = System.currentTimeMillis();
            
            // Execute streaming request
            client.createChatCompletionStream(
                request,
                // Handle each chunk
                chunk -> {
                    long currentTime = System.currentTimeMillis();
                    counters[0]++; // chunkCount
                    
                    String content = chunk.getContent();
                    if (content != null) {
                        // 记录第一个和第二个包含内容的chunk，即使内容是空字符串
                        if (tokenTimes[0] == -1) {
                            tokenTimes[0] = currentTime;
                            tokenContents[0] = content;
                            counters[1]++; // tokenCount
                        } else if (tokenTimes[1] == -1) {
                            tokenTimes[1] = currentTime;
                            tokenContents[1] = content;
                            counters[1]++; // tokenCount
                        } else {
                            counters[1]++; // tokenCount
                        }
                        
                        // 只将非空内容添加到完整响应中
                        if (!content.isEmpty()) {
                            fullResponse.append(content);
                        }
                    }
                },
                // On complete
                latch::countDown,
                // On error
                error -> {
                    streamError[0] = error;
                    System.err.println("[Thread " + Thread.currentThread().getName() + "] Error: " + error.getMessage());
                    latch.countDown();
                }
            );
            
            // Wait for completion
            latch.await();
            
            // Check for errors
            if (streamError[0] != null) {
                throw new RuntimeException("Streaming failed", streamError[0]);
            }
            
            Long firstTokenTime = tokenTimes[0] != -1 ? tokenTimes[0] : null;
            Long secondTokenTime = tokenTimes[1] != -1 ? tokenTimes[1] : null;
            String firstTokenContent = tokenContents[0];
            String secondTokenContent = tokenContents[1];
            
            // Calculate latencies
            Double firstTokenLatency = firstTokenTime != null ? 
                (double)(firstTokenTime - startTime) : null;
            Double secondTokenLatency = secondTokenTime != null ? 
                (double)(secondTokenTime - startTime) : null;
            
            return new TestResult(
                modelName, sampleQuestion, firstTokenContent, secondTokenContent,
                firstTokenLatency, secondTokenLatency, counters[1], 
                fullResponse.length(), fullResponse.toString()
            );
            
        } catch (Exception e) {
            System.err.println("[Thread " + Thread.currentThread().getName() + "] Model " + 
                             modelName + " test failed: " + e.getMessage());
            return new TestResult(modelName, sampleQuestion, e.getMessage());
        }
    }
    
    
    // Run Claude test with rate limiting
    private static TestResult runClaudeTest(String modelName, String sample, int idx) 
            throws InterruptedException {
        claudeSemaphore.acquire();
        try {
            checkClaudeRateLimit();
            System.out.println("[Claude] Testing sample " + idx + ": " + 
                             sample.substring(0, Math.min(30, sample.length())) + "...");
            return timingStreamDemo(modelName, claudeClient, sample);
        } finally {
            claudeSemaphore.release();
        }
    }
    
    
    // Test Azure models
    private static List<TestResult> testAzureModels(List<String> azureModels, List<TestResult> allResults) throws Exception {
        List<TestResult> azureResults = new ArrayList<>();
        int samplesPerModel = 40;
        ExecutorService azureExecutor = Executors.newFixedThreadPool(40);
        
        try {
            for (String model : azureModels) {
                System.out.println("\nTesting Azure model: " + model);
                List<String> samples = getRandomSamples(samplesPerModel);
                List<Future<TestResult>> futures = new ArrayList<>();
                
                for (int i = 0; i < samples.size(); i++) {
                    final String sample = samples.get(i);
                    final int idx = i + 1;
                    Future<TestResult> future = azureExecutor.submit(() -> {
                        System.out.println("[Azure-" + model + "] Testing sample " + idx + "/" + samplesPerModel + ": " + 
                                         sample.substring(0, Math.min(30, sample.length())) + "...");
                        return timingStreamDemo(model, azureClient, sample);
                    });
                    futures.add(future);
                    
                    // 添加小延迟避免瞬间发送所有请求
                    Thread.sleep(50); // 50ms延迟
                }
                
                // Collect results for this model
                for (Future<TestResult> future : futures) {
                    TestResult result = future.get();
                    azureResults.add(result);
                    allResults.add(result);
                }
            }
        } finally {
            azureExecutor.shutdown();
            azureExecutor.awaitTermination(60, TimeUnit.SECONDS);
        }
        
        return azureResults;
    }
    
    // Test Bedrock model
    private static List<TestResult> testBedrockModel(String bedrockModel, List<TestResult> allResults) throws Exception {
        List<TestResult> bedrockResults = new ArrayList<>();
        int samplesCount = 40;
        ExecutorService bedrockExecutor = Executors.newFixedThreadPool(40);
        
        try {
            System.out.println("\nTesting Bedrock model: " + bedrockModel);
            List<String> samples = getRandomSamples(samplesCount);
            List<Future<TestResult>> futures = new ArrayList<>();
            
            for (int i = 0; i < samples.size(); i++) {
                final String sample = samples.get(i);
                final int idx = i + 1;
                Future<TestResult> future = bedrockExecutor.submit(() -> {
                    System.out.println("[Bedrock] Testing sample " + idx + "/" + samplesCount + ": " + 
                                     sample.substring(0, Math.min(30, sample.length())) + "...");
                    return timingStreamDemo(bedrockModel, bedrockClient, sample);
                });
                futures.add(future);
            }
            
            // Collect results
            for (Future<TestResult> future : futures) {
                TestResult result = future.get();
                bedrockResults.add(result);
                allResults.add(result);
            }
        } finally {
            bedrockExecutor.shutdown();
            bedrockExecutor.awaitTermination(60, TimeUnit.SECONDS);
        }
        
        return bedrockResults;
    }
    
    // Test DeepSeek model
    private static List<TestResult> testDeepSeekModel(String deepseekModel, List<TestResult> allResults) throws Exception {
        List<TestResult> deepseekResults = new ArrayList<>();
        int samplesCount = 40;
        ExecutorService deepseekExecutor = Executors.newFixedThreadPool(40);
        
        try {
            System.out.println("\nTesting DeepSeek model: " + deepseekModel);
            List<String> samples = getRandomSamples(samplesCount);
            List<Future<TestResult>> futures = new ArrayList<>();
            
            // DeepSeek now runs with 40 concurrent threads
            for (int i = 0; i < samples.size(); i++) {
                final String sample = samples.get(i);
                final int idx = i + 1;
                Future<TestResult> future = deepseekExecutor.submit(() -> {
                    System.out.println("[DeepSeek] Testing sample " + idx + "/" + samplesCount + ": " + 
                                     sample.substring(0, Math.min(30, sample.length())) + "...");
                    return timingStreamDemo(deepseekModel, deepseekClient, sample);
                });
                futures.add(future);
            }
            
            // Collect results
            for (Future<TestResult> future : futures) {
                TestResult result = future.get();
                deepseekResults.add(result);
                allResults.add(result);
            }
        } finally {
            deepseekExecutor.shutdown();
            deepseekExecutor.awaitTermination(60, TimeUnit.SECONDS);
        }
        
        return deepseekResults;
    }
    
    // Test Claude model
    private static List<TestResult> testClaudeModel(String claudeModel, List<TestResult> allResults) throws Exception {
        List<TestResult> claudeResults = new ArrayList<>();
        int samplesCount = 40;
        
        System.out.println("\nTesting Claude model: " + claudeModel);
        List<String> samples = getRandomSamples(samplesCount);
        
        // Claude runs in single thread with rate limiting
        for (int i = 0; i < samples.size(); i++) {
            try {
                TestResult result = runClaudeTest(claudeModel, samples.get(i), i + 1);
                claudeResults.add(result);
                allResults.add(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return claudeResults;
    }
    
    // Export results to CSV
    private static void exportToCSV(List<TestResult> results, String filename) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            // Write header with BOM for Excel compatibility
            writer.write('\ufeff'); // UTF-8 BOM
            writer.write("model,sample,first_token,second_token,first_token_latency_ms," +
                       "second_token_latency_ms,total_tokens,response_length,full_response,error\n");
            
            // Write data
            for (TestResult result : results) {
                // Escape special characters in CSV fields
                String escapedModel = escapeCSV(result.model);
                String escapedSample = escapeCSV(result.sample);
                String escapedFirstToken = escapeCSV(result.firstToken);
                String escapedSecondToken = escapeCSV(result.secondToken);
                String escapedFullResponse = escapeCSV(result.fullResponse);
                String escapedError = escapeCSV(result.error);
                
                writer.write(String.format("%s,%s,%s,%s,%s,%s,%d,%d,%s,%s\n",
                    escapedModel,
                    escapedSample,
                    escapedFirstToken,
                    escapedSecondToken,
                    result.firstTokenLatencyMs != null ? result.firstTokenLatencyMs : "",
                    result.secondTokenLatencyMs != null ? result.secondTokenLatencyMs : "",
                    result.totalTokens,
                    result.responseLength,
                    escapedFullResponse,
                    escapedError
                ));
            }
        }
    }
    
    // Helper method to properly escape CSV fields
    private static String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        
        // Replace line breaks with spaces
        value = value.replace("\r", " ").replace("\n", " ");
        
        // If field contains comma, quotes, or other special characters, wrap in quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            // Escape quotes by doubling them
            value = value.replace("\"", "\"\"");
            // Wrap in quotes
            return "\"" + value + "\"";
        }
        
        return value;
    }
    
    public static void main(String[] args) throws Exception {
        // Test model configurations
//        List<String> azureModels = Arrays.asList("o3", "gpt-4.1", "gpt-4o-mini");
        List<String> azureModels = Arrays.asList("gpt-4o-mini");
        String deepseekModel = "ep-20250207153843-p6tc7";
        String claudeModel = "claude-3-7-sonnet-20250219";
        String bedrockModel = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";
        
        // Collect all results
        List<TestResult> allResults = Collections.synchronizedList(new ArrayList<>());
        
        System.out.println("Starting streaming latency tests...");
        System.out.println("=".repeat(80));
        
        // Create a thread for each model type test
        ExecutorService mainExecutor = Executors.newFixedThreadPool(4);
        List<Future<?>> mainFutures = new ArrayList<>();
        
        try {
            // Test Azure models in separate thread
            Future<?> azureFuture = mainExecutor.submit(() -> {
                try {
                    testAzureModels(azureModels, allResults);
                } catch (Exception e) {
                    System.err.println("Azure models test failed: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            mainFutures.add(azureFuture);
            
            // Test Bedrock model in separate thread
//            Future<?> bedrockFuture = mainExecutor.submit(() -> {
//                try {
//                    testBedrockModel(bedrockModel, allResults);
//                } catch (Exception e) {
//                    System.err.println("Bedrock model test failed: " + e.getMessage());
//                    e.printStackTrace();
//                }
//            });
//            mainFutures.add(bedrockFuture);
            
//             Test DeepSeek model in separate thread
//            Future<?> deepseekFuture = mainExecutor.submit(() -> {
//                try {
//                    testDeepSeekModel(deepseekModel, allResults);
//                } catch (Exception e) {
//                    System.err.println("DeepSeek model test failed: " + e.getMessage());
//                    e.printStackTrace();
//                }
//            });
//            mainFutures.add(deepseekFuture);
            
            // Test Claude model in separate thread
//            Future<?> claudeFuture = mainExecutor.submit(() -> {
//                try {
//                    testClaudeModel(claudeModel, allResults);
//                } catch (Exception e) {
//                    System.err.println("Claude model test failed: " + e.getMessage());
//                    e.printStackTrace();
//                }
//            });
//            mainFutures.add(claudeFuture);
            
            System.out.println("\nRunning tests for all models in parallel...");
            System.out.println("Azure models: 40 concurrent threads per model");
            System.out.println("Bedrock model: 40 concurrent threads");
            System.out.println("DeepSeek model: 40 concurrent threads");
            System.out.println("Claude model: Single thread with rate limiting\n");
            
            // Wait for all model tests to complete
            for (Future<?> future : mainFutures) {
                try {
                    future.get();
                } catch (Exception e) {
                    System.err.println("Model test execution failed: " + e.getMessage());
                }
            }
            
        } finally {
            mainExecutor.shutdown();
            if (!mainExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                mainExecutor.shutdownNow();
            }
        }
        
        System.out.println("\nAll tasks completed, collected " + allResults.size() + " results");
        
        // Save results to CSV
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "streaming_latency_results_" + timestamp + ".csv";
        exportToCSV(allResults, filename);
        
        System.out.println("\n=== Test completed ===");
        System.out.println("Results saved to: " + filename);
        
        // Display statistics summary
        System.out.println("\n=== Statistics Summary ===");
        List<String> allModels = new ArrayList<>(azureModels);
//        List<String> allModels = new ArrayList<>();
//        allModels.add(deepseekModel);
//        allModels.add(claudeModel);
//        allModels.add(bedrockModel);
        
        for (String model : allModels) {
            List<TestResult> modelResults = allResults.stream()
                .filter(r -> r.model.equals(model))
                .collect(Collectors.toList());
            
            if (!modelResults.isEmpty()) {
                System.out.println("\nModel: " + model);
                System.out.println("  Sample count: " + modelResults.size());
                
                // Calculate statistics for first token latency
                List<Double> validFirstLatencies = modelResults.stream()
                    .map(r -> r.firstTokenLatencyMs)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                if (!validFirstLatencies.isEmpty()) {
                    double avgFirst = validFirstLatencies.stream()
                        .mapToDouble(Double::doubleValue).average().orElse(0);
                    double minFirst = validFirstLatencies.stream()
                        .mapToDouble(Double::doubleValue).min().orElse(0);
                    double maxFirst = validFirstLatencies.stream()
                        .mapToDouble(Double::doubleValue).max().orElse(0);
                    
                    System.out.printf("  Average first token latency: %.2f ms\n", avgFirst);
                    System.out.printf("  Min first token latency: %.2f ms\n", minFirst);
                    System.out.printf("  Max first token latency: %.2f ms\n", maxFirst);
                }
                
                // Calculate statistics for second token latency
                List<Double> validSecondLatencies = modelResults.stream()
                    .map(r -> r.secondTokenLatencyMs)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                if (!validSecondLatencies.isEmpty()) {
                    double avgSecond = validSecondLatencies.stream()
                        .mapToDouble(Double::doubleValue).average().orElse(0);
                    System.out.printf("  Average second token latency: %.2f ms\n", avgSecond);
                }
                
                // Count errors
                long errorCount = modelResults.stream()
                    .filter(r -> r.error != null)
                    .count();
                if (errorCount > 0) {
                    System.out.println("  Error count: " + errorCount);
                }
            }
        }
    }
    
    // Get random samples from the question list
    private static List<String> getRandomSamples(int count) {
        List<String> samples = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            samples.add(SAMPLE_QUESTIONS.get(random.nextInt(SAMPLE_QUESTIONS.size())));
        }
        return samples;
    }
}