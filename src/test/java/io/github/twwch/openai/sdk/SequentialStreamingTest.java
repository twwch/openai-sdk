package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class SequentialStreamingTest {
    
    // Test result class (复用原来的)
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
        
        TestResult(String model, String sample, String error) {
            this.model = model;
            this.sample = sample;
            this.error = error;
            this.totalTokens = 0;
            this.responseLength = 0;
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
    
    public static void main(String[] args) throws Exception {
        int sampleCount = 40;
        
        // 测试配置
        boolean testAzure = false;
        boolean testBedrock = true;
        
        List<TestResult> allResults = new ArrayList<>();
        
        // 测试Azure GPT-4o-mini
        if (testAzure) {
            String azureModel = "gpt-4o-mini";
            String azureToken = System.getenv("AZURE_TOKEN");
            if (azureToken == null || azureToken.isEmpty()) {
                azureToken = "1"; // 测试用
            }
            
            AzureOpenAIConfig azureConfig = new AzureOpenAIConfig(
                azureToken,
                "iweaver-ai2",
                azureModel,
                "2025-04-01-preview"
            );
            OpenAI azureClient = new OpenAI(azureConfig);
            
            System.out.println("开始顺序测试 Azure " + azureModel);
            System.out.println("样本数量: " + sampleCount);
            System.out.println("=".repeat(80));
            
            List<String> azureSamples = getRandomSamples(sampleCount);
            List<TestResult> azureResults = new ArrayList<>();
            
            for (int i = 0; i < azureSamples.size(); i++) {
                String sample = azureSamples.get(i);
                System.out.println("\n[Azure " + (i + 1) + "/" + sampleCount + "] 测试: " + 
                                 sample.substring(0, Math.min(30, sample.length())) + "...");
                
                TestResult result = testSingleRequest(azureModel, azureClient, sample);
                azureResults.add(result);
                allResults.add(result);
                
                if (result.firstTokenLatencyMs != null) {
                    System.out.println("  首token延迟: " + result.firstTokenLatencyMs + "ms");
                }
                if (result.error != null) {
                    System.out.println("  错误: " + result.error);
                }
                
                Thread.sleep(100);
            }
            
            System.out.println("\n" + "=".repeat(80));
            printStatistics(azureResults, "Azure " + azureModel);
        }
        
        // 测试Bedrock
        if (testBedrock) {
            Thread.sleep(2000); // Azure和Bedrock测试之间等待2秒
            
            String bedrockModel = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";

            // 配置Bedrock客户端

            String bearerKey = System.getenv("AWS_BEARER_KEY_BEDROCK");
            String bearerToken = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
            OpenAI bedrockClient;
            bedrockClient = OpenAI.bedrock("us-east-2", bearerKey, bearerToken,
                    "us.anthropic.claude-3-7-sonnet-20250219-v1:0");
            if (bearerKey != null && bearerToken != null) {
                System.out.println("使用bearerKey连接Bedrock");
                bedrockClient = OpenAI.bedrock("us-east-2", bearerKey, bearerToken, bedrockModel);
            } else {
                // 使用代理 - 修正URL并使用HTTP避免SSL问题
                System.out.println("使用代理连接Bedrock");
                OpenAIConfig proxyConfig = new OpenAIConfig(
                    "1",
                    "http://Bedroc-Proxy-zxoyIf91A9jM-2088680448.us-east-2.elb.amazonaws.com/api/v1"
                );
                bedrockClient = new OpenAI(proxyConfig);
            }
            
            System.out.println("\n开始顺序测试 Bedrock " + bedrockModel);
            System.out.println("样本数量: " + sampleCount);
            System.out.println("=".repeat(80));
            
            List<String> bedrockSamples = getRandomSamples(sampleCount);
            List<TestResult> bedrockResults = new ArrayList<>();
            
            for (int i = 0; i < bedrockSamples.size(); i++) {
                String sample = bedrockSamples.get(i);
                System.out.println("\n[Bedrock " + (i + 1) + "/" + sampleCount + "] 测试: " + 
                                 sample.substring(0, Math.min(30, sample.length())) + "...");
                
                TestResult result = testSingleRequest(bedrockModel, bedrockClient, sample);
                bedrockResults.add(result);
                allResults.add(result);
                
                if (result.firstTokenLatencyMs != null) {
                    System.out.println("  首token延迟: " + result.firstTokenLatencyMs + "ms");
                }
                if (result.error != null) {
                    System.out.println("  错误: " + result.error);
                }
                
                Thread.sleep(100);
            }
            
            System.out.println("\n" + "=".repeat(80));
            printStatistics(bedrockResults, "Bedrock " + bedrockModel);
        }
        
        // 保存所有结果
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "sequential_all_results_" + timestamp + ".csv";
        exportToCSV(allResults, filename);
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("所有测试完成");
        System.out.println("结果已保存到: " + filename);
    }
    
    private static TestResult testSingleRequest(String modelName, OpenAI client, String sampleQuestion) {
        StringBuilder fullResponse = new StringBuilder();
        
        try {
            // 创建请求
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel(modelName);
            request.setMessages(Collections.singletonList(
                ChatMessage.user(sampleQuestion)
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
            
            // 时间统计
            CountDownLatch latch = new CountDownLatch(1);
            final long[] tokenTimes = {-1, -1};
            final String[] tokenContents = {null, null};
            final int[] tokenCount = {0};
            final Throwable[] streamError = {null};
            
            // 记录开始时间
            long startTime = System.currentTimeMillis();
            
            // 发送请求
            client.createChatCompletionStream(
                request,
                chunk -> {
                    long currentTime = System.currentTimeMillis();
                    String content = chunk.getContent();
                    
                    if (content != null) {
                        if (tokenTimes[0] == -1) {
                            tokenTimes[0] = currentTime;
                            tokenContents[0] = content;
                        } else if (tokenTimes[1] == -1) {
                            tokenTimes[1] = currentTime;
                            tokenContents[1] = content;
                        }
                        
                        tokenCount[0]++;
                        if (!content.isEmpty()) {
                            fullResponse.append(content);
                        }
                    }
                },
                latch::countDown,
                error -> {
                    streamError[0] = error;
                    latch.countDown();
                }
            );
            
            // 等待完成
            latch.await();
            
            if (streamError[0] != null) {
                return new TestResult(modelName, sampleQuestion, streamError[0].getMessage());
            }
            
            // 计算延迟
            Double firstTokenLatency = tokenTimes[0] != -1 ? 
                (double)(tokenTimes[0] - startTime) : null;
            Double secondTokenLatency = tokenTimes[1] != -1 ? 
                (double)(tokenTimes[1] - startTime) : null;
            
            return new TestResult(
                modelName, sampleQuestion, tokenContents[0], tokenContents[1],
                firstTokenLatency, secondTokenLatency, tokenCount[0], 
                fullResponse.length(), fullResponse.toString()
            );
            
        } catch (Exception e) {
            return new TestResult(modelName, sampleQuestion, e.getMessage());
        }
    }
    
    private static List<String> getRandomSamples(int count) {
        List<String> samples = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            samples.add(SAMPLE_QUESTIONS.get(random.nextInt(SAMPLE_QUESTIONS.size())));
        }
        return samples;
    }
    
    private static void exportToCSV(List<TestResult> results, String filename) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write('\ufeff'); // UTF-8 BOM
            writer.write("model,sample,first_token,second_token,first_token_latency_ms," +
                       "second_token_latency_ms,total_tokens,response_length,full_response,error\n");
            
            for (TestResult result : results) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s,%d,%d,%s,%s\n",
                    escapeCSV(result.model),
                    escapeCSV(result.sample),
                    escapeCSV(result.firstToken),
                    escapeCSV(result.secondToken),
                    result.firstTokenLatencyMs != null ? result.firstTokenLatencyMs : "",
                    result.secondTokenLatencyMs != null ? result.secondTokenLatencyMs : "",
                    result.totalTokens,
                    result.responseLength,
                    escapeCSV(result.fullResponse),
                    escapeCSV(result.error)
                ));
            }
        }
    }
    
    private static String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        
        value = value.replace("\r", " ").replace("\n", " ");
        
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }
        
        return value;
    }
    
    private static void printStatistics(List<TestResult> results, String model) {
        System.out.println("\n统计摘要:");
        System.out.println("模型: " + model);
        System.out.println("样本数: " + results.size());
        
        List<Double> validFirstLatencies = new ArrayList<>();
        List<Double> validSecondLatencies = new ArrayList<>();
        
        for (TestResult result : results) {
            if (result.firstTokenLatencyMs != null) {
                validFirstLatencies.add(result.firstTokenLatencyMs);
            }
            if (result.secondTokenLatencyMs != null) {
                validSecondLatencies.add(result.secondTokenLatencyMs);
            }
        }
        
        if (!validFirstLatencies.isEmpty()) {
            double avgFirst = validFirstLatencies.stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);
            double minFirst = validFirstLatencies.stream()
                .mapToDouble(Double::doubleValue).min().orElse(0);
            double maxFirst = validFirstLatencies.stream()
                .mapToDouble(Double::doubleValue).max().orElse(0);
            
            System.out.printf("平均首token延迟: %.2f ms\n", avgFirst);
            System.out.printf("最小首token延迟: %.2f ms\n", minFirst);
            System.out.printf("最大首token延迟: %.2f ms\n", maxFirst);
        }
        
        if (!validSecondLatencies.isEmpty()) {
            double avgSecond = validSecondLatencies.stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);
            System.out.printf("平均第二token延迟: %.2f ms\n", avgSecond);
        }
        
        long errorCount = results.stream()
            .filter(r -> r.error != null)
            .count();
        if (errorCount > 0) {
            System.out.println("错误数: " + errorCount);
        }
    }
}