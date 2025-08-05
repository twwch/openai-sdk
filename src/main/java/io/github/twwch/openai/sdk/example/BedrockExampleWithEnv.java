package io.github.twwch.openai.sdk.example;

import io.github.twwch.openai.sdk.OpenAI;

/**
 * AWS Bedrock使用示例 - 使用环境变量
 */
public class BedrockExampleWithEnv {
    public static void main(String[] args) {
        System.out.println("=== AWS Bedrock 示例（使用环境变量）===");
        
        // 方案1：使用标准 AWS 凭证环境变量
        // 需要设置: AWS_ACCESS_KEY_ID 和 AWS_SECRET_ACCESS_KEY
        String awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
        String awsSecretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        
        if (awsAccessKeyId != null && awsSecretAccessKey != null) {
            System.out.println("使用标准 AWS 凭证");
            testWithCredentials(awsAccessKeyId, awsSecretAccessKey);
        }
        
        // 方案2：使用会话令牌（如果有的话）
        String sessionToken = System.getenv("AWS_SESSION_TOKEN");
        
        if (awsAccessKeyId != null && awsSecretAccessKey != null && sessionToken != null) {
            System.out.println("\n使用带会话令牌的 AWS 凭证");
            testWithSessionCredentials(awsAccessKeyId, awsSecretAccessKey, sessionToken);
        }
        
        // 方案3：使用默认凭证链（AWS 配置文件、实例角色等）
        System.out.println("\n使用默认 AWS 凭证链");
        testWithDefaultCredentials();
    }
    
    private static void testWithCredentials(String accessKey, String secretKey) {
        try {
            // 使用正确的模型 ID
            String modelId = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";
            OpenAI client = OpenAI.bedrock("us-east-1", accessKey, secretKey, modelId);
            
            // 简单测试
            String response = client.chat(modelId, "你好！请简单介绍一下自己。");
            System.out.println("响应: " + response);
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testWithSessionCredentials(String accessKey, String secretKey, String sessionToken) {
        try {
            // 使用正确的模型 ID
            String modelId = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";
            OpenAI client = OpenAI.bedrock("us-east-1", accessKey, secretKey, sessionToken, modelId);
            
            // 简单测试
            String response = client.chat(modelId, "你好！请简单介绍一下自己。");
            System.out.println("响应: " + response);
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testWithDefaultCredentials() {
        try {
            // 使用默认凭证
            String modelId = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";
            OpenAI client = OpenAI.bedrock("us-east-1", modelId);
            
            // 简单测试
            String response = client.chat(modelId, "你好！");
            System.out.println("响应: " + response);
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
        }
    }
}