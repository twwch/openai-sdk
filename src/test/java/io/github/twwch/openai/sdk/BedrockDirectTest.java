package io.github.twwch.openai.sdk;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

/**
 * 直接测试AWS Bedrock API
 */
public class BedrockDirectTest {
    
    public static void main(String[] args) throws Exception {
        // 从环境变量获取凭证
        String bearerKey = System.getenv("AWS_BEARER_KEY_BEDROCK");
        String bearerToken = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
        
        if (bearerKey == null || bearerToken == null) {
            System.err.println("请设置环境变量 AWS_BEARER_KEY_BEDROCK 和 AWS_BEARER_TOKEN_BEDROCK");
            System.exit(1);
        }
        
        // 创建凭证
        AwsSessionCredentials credentials = AwsSessionCredentials.create(
            bearerKey,
            bearerToken,
            null
        );
        
        // 创建Bedrock客户端
        BedrockRuntimeAsyncClient client = BedrockRuntimeAsyncClient.builder()
                .region(Region.US_EAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
        
        String modelId = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";
        
        // 测试1: 最小请求
        System.out.println("=== 测试1: 最小请求 ===");
        testMinimalRequest(client, modelId);
        
        // 测试2: 官方示例格式
        System.out.println("\n=== 测试2: 官方示例格式 ===");
        testOfficialFormat(client, modelId);
        
        // 等待异步操作完成
        Thread.sleep(5000);
        client.close();
    }
    
    // 测试最小请求
    private static void testMinimalRequest(BedrockRuntimeAsyncClient client, String modelId) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = mapper.createObjectNode();
        
        // 只包含绝对必需的参数
        request.put("anthropic_version", "bedrock-2023-05-31");
        request.put("max_tokens", 100);
        
        ArrayNode messages = request.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", "Hello");
        
        String requestBody = mapper.writeValueAsString(request);
        System.out.println("请求内容: " + requestBody);
        
        executeStreamRequest(client, modelId, requestBody, "最小请求");
    }
    
    // 测试官方示例格式
    private static void testOfficialFormat(BedrockRuntimeAsyncClient client, String modelId) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = mapper.createObjectNode();
        
        // 按照官方文档的格式
        request.put("anthropic_version", "bedrock-2023-05-31");
        request.put("max_tokens", 512);
        request.put("temperature", 0.5);
        
        ArrayNode messages = request.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        
        // 官方示例使用content数组格式
        ArrayNode content = userMessage.putArray("content");
        ObjectNode textContent = content.addObject();
        textContent.put("type", "text");
        textContent.put("text", "Describe the purpose of a 'hello world' program in one line.");
        
        String requestBody = mapper.writeValueAsString(request);
        System.out.println("请求内容: " + requestBody);
        
        executeStreamRequest(client, modelId, requestBody, "官方格式");
    }
    
    // 执行流式请求
    private static void executeStreamRequest(BedrockRuntimeAsyncClient client, String modelId, 
                                           String requestBody, String testName) {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder response = new StringBuilder();
        
        try {
            InvokeModelWithResponseStreamRequest request = InvokeModelWithResponseStreamRequest.builder()
                    .modelId(modelId)
                    .body(SdkBytes.fromString(requestBody, StandardCharsets.UTF_8))
                    .contentType("application/json")
                    .accept("application/json")
                    .build();
            
            InvokeModelWithResponseStreamResponseHandler handler = InvokeModelWithResponseStreamResponseHandler.builder()
                    .subscriber(event -> {
                        if (event instanceof PayloadPart) {
                            PayloadPart payloadPart = (PayloadPart) event;
                            String chunk = payloadPart.bytes().asUtf8String();
                            response.append(chunk);
                            System.out.print(".");
                        }
                    })
                    .onComplete(() -> {
                        System.out.println("\n" + testName + " - 成功");
                        System.out.println("响应: " + response.toString());
                        latch.countDown();
                    })
                    .onError(throwable -> {
                        System.err.println("\n" + testName + " - 失败");
                        System.err.println("错误类型: " + throwable.getClass().getName());
                        System.err.println("错误消息: " + throwable.getMessage());
                        throwable.printStackTrace();
                        latch.countDown();
                    })
                    .build();
            
            client.invokeModelWithResponseStream(request, handler);
            
            latch.await();
            
        } catch (Exception e) {
            System.err.println(testName + " - 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}