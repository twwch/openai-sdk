# Prompt Caching 功能总结

本SDK已全面支持AWS Bedrock和Azure OpenAI的Prompt Caching功能,可节省高达90-100%的输入token成本。

## 快速对比

| 特性 | AWS Bedrock | Azure OpenAI |
|------|------------|--------------|
| **配置难度** | 中等 - 需要代码标记 | 简单 - 完全自动 |
| **成本节省** | 90% | 90-100% |
| **控制精度** | 精细(system/content) | 自动检测 |
| **API版本要求** | 无 | 2024-10-01-preview+ |
| **最小tokens** | 1024-4096 | 1024 |
| **文档** | [BEDROCK_PROMPT_CACHING.md](BEDROCK_PROMPT_CACHING.md) | [AZURE_PROMPT_CACHING.md](AZURE_PROMPT_CACHING.md) |

## AWS Bedrock 使用示例

```java
// 方式1: System消息缓存
ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("anthropic.claude-3-5-sonnet-20241022-v2:0");
request.setBedrockEnableSystemCache(true); // 启用缓存
request.setMessages(Arrays.asList(
    ChatMessage.system("长系统提示...[1024+ tokens]"),
    ChatMessage.user("问题")
));

// 方式2: Content精细控制
ChatMessage.user(
    ChatMessage.ContentPart.textWithCache("长上下文...", true),
    ChatMessage.ContentPart.text("问题")
)
```

## Azure OpenAI 使用示例

```java
// 只需使用正确的API版本,其他完全自动
OpenAI client = OpenAI.azure(
    apiKey,
    resourceName,
    deploymentId,
    "2024-10-01-preview"  // 关键!
);

// 正常使用,Azure自动处理缓存
ChatCompletionRequest request = new ChatCompletionRequest();
request.setMessages(Arrays.asList(
    ChatMessage.system("长系统提示...[1024+ tokens]"),
    ChatMessage.user("问题")
));
```

## 查看缓存统计

两个平台都会在响应中返回缓存统计:

```java
ChatCompletionResponse response = client.createChatCompletion(request);
ChatCompletionResponse.Usage usage = response.getUsage();

// 缓存创建
Integer created = usage.getCacheCreationInputTokens();
if (created != null && created > 0) {
    System.out.println("创建缓存: " + created + " tokens");
}

// 缓存命中
Integer cached = usage.getCacheReadInputTokens();
if (cached != null && cached > 0) {
    System.out.println("缓存命中: " + cached + " tokens (节省90%!)");
}
```

## 测试代码

运行测试查看实际效果:

```bash
# Bedrock测试
mvn test -Dtest=BedrockPromptCachingTest

# 使用自定义配置
mvn test -Dtest=BedrockPromptCachingTest \
  -Dbedrock.region=us-west-2 \
  -Dbedrock.modelId=anthropic.claude-3-7-sonnet-20250219-v1:0
```

## 何时使用

**适合使用Prompt Caching的场景:**
- ✅ RAG应用(重复检索相同文档)
- ✅ 代码审查(同一代码库多次分析)
- ✅ 长对话历史
- ✅ 固定的系统提示词
- ✅ 需要多次询问同一上下文

**不适合的场景:**
- ❌ 每次请求都完全不同
- ❌ 内容少于1024 tokens
- ❌ 只调用1-2次(成本可能更高)

## 成本收益分析

### AWS Bedrock

假设标准输入价格: $0.003/1K tokens

| 请求次数 | 无缓存成本 | 使用缓存成本 | 节省 |
|---------|----------|------------|------|
| 1次 | $0.030 | $0.0375 | -$0.0075 |
| 3次 | $0.090 | $0.0435 | $0.0465 (52%) |
| 10次 | $0.300 | $0.0675 | $0.2325 (78%) |
| 100次 | $3.000 | $0.3375 | $2.6625 (89%) |

### Azure OpenAI (Standard)

节省率与Bedrock类似(~90%),Provisioned部署可能达到100%免费。

## 技术实现

### 新增数据模型

1. **ChatMessage.ContentPart.CacheControl** - 缓存控制标记
2. **ChatCompletionRequest.bedrockEnableSystemCache** - System缓存开关
3. **ChatCompletionResponse.Usage.cacheReadInputTokens** - 缓存读取统计
4. **ChatCompletionResponse.Usage.cacheCreationInputTokens** - 缓存创建统计

### 修改的组件

- **ClaudeModelAdapter** - 支持cache_control转换和usage统计
- **ChatMessage** - 添加CacheControl支持
- **ChatCompletionRequest/Response** - 添加缓存相关字段

## 更多信息

- [Bedrock详细文档](BEDROCK_PROMPT_CACHING.md)
- [Azure详细文档](AZURE_PROMPT_CACHING.md)
- [测试代码](src/test/java/io/github/twwch/openai/sdk/BedrockPromptCachingTest.java)
