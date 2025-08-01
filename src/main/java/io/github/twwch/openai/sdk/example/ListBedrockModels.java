package io.github.twwch.openai.sdk.example;

/**
 * AWS Bedrock 支持的模型列表
 */
public class ListBedrockModels {
    public static void main(String[] args) {
        System.out.println("=== AWS Bedrock 支持的模型 ID ===\n");
        
        System.out.println("Claude (Anthropic):");
        System.out.println("- anthropic.claude-3-opus-20240229");
        System.out.println("- anthropic.claude-3-sonnet-20240229");
        System.out.println("- anthropic.claude-3-haiku-20240307");
        System.out.println("- anthropic.claude-v2:1");
        System.out.println("- anthropic.claude-v2");
        System.out.println("- anthropic.claude-instant-v1");
        
        System.out.println("\nLlama 2 (Meta):");
        System.out.println("- meta.llama2-70b-chat-v1");
        System.out.println("- meta.llama2-13b-chat-v1");
        
        System.out.println("\nTitan (Amazon):");
        System.out.println("- amazon.titan-text-express-v1");
        System.out.println("- amazon.titan-text-lite-v1");
        
        System.out.println("\n注意：");
        System.out.println("1. 模型可用性因区域而异");
        System.out.println("2. 某些模型可能需要特殊权限或订阅");
        System.out.println("3. 模型 ID 必须完全匹配（包括版本号）");
        
        System.out.println("\n你使用的模型 ID：");
        System.out.println("'anthropic.claude-3-7-sonnet-20250219-v1:0' 看起来不正确");
        System.out.println("应该使用：'anthropic.claude-3-sonnet-20240229'");
    }
}