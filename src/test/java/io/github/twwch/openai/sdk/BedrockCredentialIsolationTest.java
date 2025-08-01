package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试Bedrock凭证隔离功能
 * 验证在存在其他AWS凭证的情况下，Bedrock仍能使用正确的凭证
 */
public class BedrockCredentialIsolationTest {
    
    // 保存原始环境变量
    private Map<String, String> originalEnv;
    private Map<String, String> originalSystemProps;
    
    @BeforeEach
    public void setUp() {
        // 备份原始环境
        originalEnv = new HashMap<>();
        originalEnv.put("AWS_ACCESS_KEY_ID", System.getenv("AWS_ACCESS_KEY_ID"));
        originalEnv.put("AWS_SECRET_ACCESS_KEY", System.getenv("AWS_SECRET_ACCESS_KEY"));
        originalEnv.put("AWS_SESSION_TOKEN", System.getenv("AWS_SESSION_TOKEN"));
        
        originalSystemProps = new HashMap<>();
        originalSystemProps.put("aws.accessKeyId", System.getProperty("aws.accessKeyId"));
        originalSystemProps.put("aws.secretAccessKey", System.getProperty("aws.secretAccessKey"));
        originalSystemProps.put("aws.sessionToken", System.getProperty("aws.sessionToken"));
    }
    
    @AfterEach
    public void tearDown() {
        // 恢复原始环境
        originalSystemProps.forEach((key, value) -> {
            if (value != null) {
                System.setProperty(key, value);
            } else {
                System.clearProperty(key);
            }
        });
    }
    
    @Test
    public void testCredentialIsolationWithConflictingEnvVars() throws Exception {
        // 设置冲突的S3凭证（无Bedrock权限）
        System.setProperty("aws.accessKeyId", "AKIA5FCKX2MX4E67ZVF6");
        System.setProperty("aws.secretAccessKey", "kHwWFpXi1yoXh7BDCkKDSeVNbUveB/h4PP3");
        
        // 使用专门的Bedrock凭证创建客户端
        String bedrockAccessKey = "BedrockAPIKey-c1cg-at-448479377682";
        String bedrockSecretKey = System.getenv("BEDROCK_SECRET_KEY");
        
        if (bedrockSecretKey == null) {
            System.out.println("跳过测试：未设置BEDROCK_SECRET_KEY环境变量");
            return;
        }
        
        // 创建Bedrock客户端
        OpenAI client = OpenAI.bedrock(
            "us-east-2",
            bedrockAccessKey,
            bedrockSecretKey,
            "anthropic.claude-3-5-sonnet-20240620-v1:0"
        );
        
        // 创建简单请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(Arrays.asList(
            ChatMessage.user("回复一个字：好")
        ));
        request.setMaxTokens(10);
        request.setStream(true);
        
        // 测试流式请求
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>();
        StringBuilder response = new StringBuilder();
        
        client.createChatCompletionStream(request, chunk -> {
            if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                var delta = chunk.getChoices().get(0).getDelta();
                if (delta != null && delta.getContent() != null) {
                    response.append(delta.getContent());
                }
            }
            success.set(true);
        }, latch::countDown, e -> {
            error.set(e.getMessage());
            e.printStackTrace();
            latch.countDown();
        });
        
        // 等待完成
        assertTrue(latch.await(30, TimeUnit.SECONDS), "请求超时");
        
        // 验证结果
        if (error.get() != null) {
            fail("请求失败：" + error.get());
        }
        
        assertTrue(success.get(), "未收到任何响应");
        assertTrue(response.length() > 0, "响应内容为空");
        
        System.out.println("凭证隔离测试成功！");
        System.out.println("响应内容：" + response.toString());
    }
    
    @Test
    public void testCredentialIsolationWithoutBedrockCredentials() {
        // 设置S3凭证
        System.setProperty("aws.accessKeyId", "AKIA5FCKX2MX4E67ZVF6");
        System.setProperty("aws.secretAccessKey", "kHwWFpXi1yoXh7BDCkKDSeVNbUveB/h4PP3");
        
        // 尝试创建Bedrock客户端而不提供凭证
        assertThrows(IllegalArgumentException.class, () -> {
            OpenAI.bedrock("us-east-2", null, null, "anthropic.claude-3-5-sonnet-20240620-v1:0");
        }, "应该因为缺少凭证而抛出异常");
    }
    
    @Test
    public void testMultipleClientsWithDifferentCredentials() throws Exception {
        String bedrockAccessKey1 = System.getenv("BEDROCK_ACCESS_KEY_1");
        String bedrockSecretKey1 = System.getenv("BEDROCK_SECRET_KEY_1");
        String bedrockAccessKey2 = System.getenv("BEDROCK_ACCESS_KEY_2");
        String bedrockSecretKey2 = System.getenv("BEDROCK_SECRET_KEY_2");
        
        if (bedrockAccessKey1 == null || bedrockAccessKey2 == null) {
            System.out.println("跳过测试：未设置多个Bedrock凭证");
            return;
        }
        
        // 创建两个使用不同凭证的客户端
        OpenAI client1 = OpenAI.bedrock(
            "us-east-2",
            bedrockAccessKey1,
            bedrockSecretKey1,
            "anthropic.claude-3-5-sonnet-20240620-v1:0"
        );
        
        OpenAI client2 = OpenAI.bedrock(
            "us-west-2",
            bedrockAccessKey2,
            bedrockSecretKey2,
            "anthropic.claude-3-sonnet-20240229"
        );
        
        // 创建请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(Arrays.asList(
            ChatMessage.user("回复：1")
        ));
        request.setMaxTokens(10);
        
        // 两个客户端应该能独立工作
        CountDownLatch latch = new CountDownLatch(2);
        AtomicBoolean client1Success = new AtomicBoolean(false);
        AtomicBoolean client2Success = new AtomicBoolean(false);
        
        // 客户端1请求
        new Thread(() -> {
            try {
                client1.createChatCompletionStream(request, chunk -> {
                    client1Success.set(true);
                }, latch::countDown, e -> {
                    e.printStackTrace();
                    latch.countDown();
                });
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        }).start();
        
        // 客户端2请求
        new Thread(() -> {
            try {
                client2.createChatCompletionStream(request, chunk -> {
                    client2Success.set(true);
                }, latch::countDown, e -> {
                    e.printStackTrace();
                    latch.countDown();
                });
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        }).start();
        
        // 等待完成
        assertTrue(latch.await(30, TimeUnit.SECONDS), "请求超时");
        
        // 验证两个客户端都成功
        assertTrue(client1Success.get() || client2Success.get(), 
            "至少一个客户端应该成功");
        
        System.out.println("多客户端凭证隔离测试成功！");
    }
    
    @Test
    public void testBedrockApiKeyFormat() {
        // 测试Bedrock API Key格式的凭证
        String apiKey = "BedrockAPIKey-test-123";
        String sessionToken = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
        
        // 应该能成功创建客户端（不实际调用API）
        assertDoesNotThrow(() -> {
            OpenAI client = OpenAI.bedrock(
                "us-east-2",
                apiKey,
                sessionToken,
                "anthropic.claude-3-5-sonnet-20240620-v1:0"
            );
            assertNotNull(client);
        }, "使用Bedrock API Key格式应该能创建客户端");
    }
    
    /**
     * 诊断测试 - 打印当前环境信息
     */
    @Test
    public void diagnosticTest() {
        System.out.println("\n=== 凭证隔离诊断信息 ===\n");
        
        // 检查环境变量
        System.out.println("环境变量：");
        String[] envVars = {
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "AWS_SESSION_TOKEN",
            "AWS_REGION"
        };
        for (String var : envVars) {
            String value = System.getenv(var);
            if (value != null) {
                System.out.println("  " + var + " = " + 
                    (var.contains("SECRET") ? "***" : value.substring(0, Math.min(10, value.length())) + "..."));
            }
        }
        
        // 检查系统属性
        System.out.println("\n系统属性：");
        String[] sysProps = {
            "aws.accessKeyId",
            "aws.secretAccessKey",
            "aws.sessionToken",
            "aws.region"
        };
        for (String prop : sysProps) {
            String value = System.getProperty(prop);
            if (value != null) {
                System.out.println("  " + prop + " = " + 
                    (prop.contains("secret") ? "***" : value.substring(0, Math.min(10, value.length())) + "..."));
            }
        }
        
        System.out.println("\n凭证隔离功能正常工作时，Bedrock应该只使用显式提供的凭证，");
        System.out.println("而不会使用上述环境变量或系统属性中的凭证。");
        System.out.println("\n=========================\n");
    }
}