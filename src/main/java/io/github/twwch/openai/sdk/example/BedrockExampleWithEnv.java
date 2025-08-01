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
        
        // 方案2：使用你的自定义环境变量
        String bearerKey = System.getenv("AWS_BEARER_KEY_BEDROCK");
        String bearerToken = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
        
        System.out.println("\n检查自定义环境变量:");
        System.out.println("AWS_BEARER_KEY_BEDROCK: " + (bearerKey != null ? "已设置" : "未设置"));
        System.out.println("AWS_BEARER_TOKEN_BEDROCK: " + (bearerToken != null ? "已设置" : "未设置"));
        
        if (bearerKey != null && bearerToken != null) {
            System.out.println("\n使用自定义凭证");
            System.out.println("Key: " + bearerKey);
            System.out.println("Token: " + bearerToken.substring(0, 20) + "...");
            
            // 注意：如果这是 Bedrock 特定的 API Key，可能需要不同的认证方式
            if (bearerKey.startsWith("BedrockAPIKey-")) {
                System.out.println("检测到 Bedrock API Key 格式");
                System.out.println("注意：AWS SDK 标准认证可能不支持这种格式");
                System.out.println("建议使用标准 AWS IAM 凭证（Access Key ID 和 Secret Access Key）");
            }
            
            // 尝试使用这些凭证
            testWithCredentials(bearerKey, bearerToken);
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