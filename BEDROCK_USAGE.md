# AWS Bedrock 使用指南

本SDK支持通过统一的OpenAI接口调用AWS Bedrock服务。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.twwch</groupId>
    <artifactId>openai-sdk</artifactId>
    <version>1.1.53</version>
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

## 并发测试脚本

### 安装 Maven (macOS)

```bash
# 使用 Homebrew 安装 Maven
brew install maven

# 验证安装
mvn --version
```

### 运行并发测试

```bash
# 1. 编译项目
mvn clean compile test-compile
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.2.jdk/Contents/Home mvn clean compile test-compile

# 2. 运行并发测试 (使用 aws configure 配置的凭证)
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.2.jdk/Contents/Home && java -cp "target/classes:target/test-classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
  -Dbedrock.region=us-west-2 \
  -Dbedrock.concurrent.requests=10 \
  -Dbedrock.total.requests=50 \
  -Dbedrock.modelId=us.anthropic.claude-3-7-sonnet-20250219-v1:0 \
  io.github.twwch.openai.sdk.ConcurrentBedrockTest

nohup env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.2.jdk/Contents/Home && java \
  -Djavax.net.ssl.trustStore=/Library/Java/JavaVirtualMachines/jdk-11.0.2.jdk/Contents/Home/lib/security/cacerts \
  -Djavax.net.ssl.trustStorePassword=changeit \
  -Djdk.tls.client.protocols=TLSv1.2 \
  -cp "target/classes:target/test-classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
  -Dbedrock.region=us-west-2 \
  -Dbedrock.concurrent.requests=3 \
  -Dbedrock.total.requests=1000 \
  -Dbedrock.http.maxConcurrency=20 \
  -Dbedrock.test.max.minutes=600 \
  -Dbedrock.http.acquireTimeoutSeconds=30 \
  -Dbedrock.http.maxPendingAcquires=20 \
  -Dbedrock.modelId=us.anthropic.claude-3-7-sonnet-20250219-v1:0 \
  io.github.twwch.openai.sdk.ConcurrentBedrockTest > >(tee -a "log_$(date +%Y%m%d_%H%M%S).log") 2>&1 &

# 3. 或者使用指定的凭证运行
java -cp "target/classes:target/test-classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
  -Dbedrock.region=us-west-2 \
  -Dbedrock.accessKeyId=YOUR_ACCESS_KEY \
  -Dbedrock.secretAccessKey=YOUR_SECRET_KEY \
  -Dbedrock.modelId=us.anthropic.claude-3-7-sonnet-20250219-v1:0 \
  io.github.twwch.openai.sdk.ConcurrentBedrockTest
```

测试将模拟30个并发请求，验证连接池管理和资源释放是否正常。

### 连接池问题排查

如果遇到连接池相关错误（如 "Acquire operation took longer than the configured maximum time"），可以：

1. **降低并发数**：
```bash
# 减少并发请求数量
java -cp "..." \
  -Dbedrock.concurrent.requests=10 \
  -Dbedrock.total.requests=50 \
  io.github.twwch.openai.sdk.ConcurrentBedrockTest
```

2. **检查连接池配置**：
   - 同步客户端最大连接数: 50
   - 异步客户端最大并发数: 20
   - 连接获取超时: 60秒
   - 连接生存时间: 3分钟

3. **优化测试参数**：
   - 减少 `CONCURRENT_REQUESTS` (默认30)
   - 减少 `TOTAL_REQUESTS` (默认100)
   - 增加请求间隔时间