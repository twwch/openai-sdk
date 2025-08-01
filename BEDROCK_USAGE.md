# AWS Bedrock 使用指南

本SDK支持通过统一的OpenAI接口调用AWS Bedrock服务。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.twwch</groupId>
    <artifactId>openai-sdk</artifactId>
    <version>1.1.9</version>
</dependency>
```

### 2. 创建客户端

```java
// 方式1：使用默认AWS凭证（从~/.aws/credentials或环境变量）
OpenAI client = OpenAI.bedrock("us-east-1", "anthropic.claude-3-sonnet-20240229");

// 方式2：使用访问密钥
OpenAI client = OpenAI.bedrock("us-east-1", accessKeyId, secretAccessKey, 
                              "anthropic.claude-3-sonnet-20240229");

// 方式3：使用临时凭证
OpenAI client = OpenAI.bedrock("us-east-1", accessKeyId, secretAccessKey, 
                              sessionToken, "anthropic.claude-3-sonnet-20240229");
```

### 3. 基本使用

```java
// 简单对话
String response = client.chat("claude", "你好，请介绍一下自己。");

// 带系统提示的对话
String response = client.chat("claude", 
    "你是一个专业的Java开发者。", 
    "如何优化Java程序的性能？");

// 流式响应
client.chatStream("claude", "写一个故事", chunk -> {
    System.out.print(chunk);
});
```

## 支持的模型

### Claude (Anthropic)
- `anthropic.claude-3-opus-20240229` - 最强大的模型
- `anthropic.claude-3-sonnet-20240229` - 平衡性能和成本
- `anthropic.claude-3-haiku-20240307` - 快速且经济
- `anthropic.claude-v2:1`
- `anthropic.claude-v2`
- `anthropic.claude-instant-v1`

### Llama 2 (Meta)
- `meta.llama2-13b-chat-v1`
- `meta.llama2-70b-chat-v1`

### Titan (Amazon)
- `amazon.titan-text-express-v1`
- `amazon.titan-text-lite-v1`

### 其他模型
- AI21 Jurassic系列
- Cohere系列

## 高级功能

### 自定义参数

```java
ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("claude");
request.setMessages(Arrays.asList(
    ChatMessage.system("你是一个有帮助的助手。"),
    ChatMessage.user("解释量子计算")
));
request.setMaxTokens(1000);
request.setTemperature(0.7);

ChatCompletionResponse response = client.createChatCompletion(request);
```

### 错误处理

```java
try {
    String response = client.chat("claude", "你好");
} catch (OpenAIException e) {
    System.err.println("调用失败: " + e.getMessage());
}
```

## 注意事项

1. **区域选择**：确保选择支持Bedrock的AWS区域
2. **模型可用性**：不同区域支持的模型可能不同
3. **权限配置**：确保AWS凭证有调用Bedrock的权限
4. **成本控制**：注意不同模型的定价差异

## AWS权限配置

需要以下IAM权限：

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "bedrock:InvokeModel",
                "bedrock:InvokeModelWithResponseStream"
            ],
            "Resource": "*"
        }
    ]
}
```

## 完整示例

参见 `src/main/java/io/github/twwch/openai/sdk/example/BedrockExample.java`