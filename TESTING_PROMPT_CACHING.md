# Prompt Caching 测试快速指南

本SDK包含两个完整的Prompt Caching测试类,用于测试AWS Bedrock和Azure OpenAI的缓存功能。

## 测试类对比

| 特性 | BedrockPromptCachingTest | AzurePromptCachingTest |
|------|-------------------------|------------------------|
| **配置方式** | 手动标记cache_control | 完全自动 |
| **测试数量** | 5个 | 3个 |
| **成本节省** | 90% | 90-100% |
| **API版本要求** | 无 | 2024-10-01-preview+ |
| **最小tokens** | 1024-4096 | 1024 |

## 快速开始

### AWS Bedrock测试

```bash
# 运行所有Bedrock测试
mvn test -Dtest=BedrockPromptCachingTest

# 运行特定测试
mvn test -Dtest=BedrockPromptCachingTest#testSystemMessageCaching
mvn test -Dtest=BedrockPromptCachingTest#testStreamingWithCaching
```

### Azure OpenAI测试

```bash
# 设置环境变量
export AZURE_OPENAI_API_KEY="your-key"
export AZURE_OPENAI_RESOURCE="your-resource"
export AZURE_OPENAI_DEPLOYMENT="gpt-4o"

# 运行所有Azure测试
mvn test -Dtest=AzurePromptCachingTest

# 或使用系统属性
mvn test -Dtest=AzurePromptCachingTest \
  -Dazure.apiKey=xxx \
  -Dazure.resourceName=xxx \
  -Dazure.deploymentId=xxx \
  -Dazure.apiVersion=2024-10-01-preview
```

## Bedrock测试列表

### 1. `testSystemMessageCaching()`
- 测试system消息缓存
- 使用`bedrockEnableSystemCache=true`
- 验证缓存创建和命中

### 2. `testContentPartCaching()`
- 测试ContentPart精细缓存
- 使用`textWithCache(content, true)`
- 缓存用户消息中的特定内容

### 3. `testCombinedCaching()`
- 组合使用system和content缓存
- 最大化成本节省
- 演示多层缓存策略

### 4. `testStreamingWithCaching()`
- 流式响应中的缓存
- 实时输出+usage统计
- 验证流式场景缓存效果

### 5. `testMultiTurnConversationCaching()`
- 多轮对话缓存
- 缓存对话历史
- 只处理新问题

## Azure测试列表

### 1. `testBasicPromptCaching()`
- Azure自动缓存验证
- 无需配置cache_control
- 自动检测重复前缀

### 2. `testStreamingWithCaching()`
- 流式响应缓存
- 实时输出
- Usage在流结束时返回

### 3. `testMultiTurnConversationCaching()`
- 多轮对话缓存
- 自动缓存历史
- 演示RAG场景

## 理解测试输出

### 成功的缓存命中 ✅

**Bedrock输出**:
```
=== 第二次请求 (应该命中缓存) ===
原始Bedrock响应usage字段: {"input_tokens":300,"output_tokens":150,"cache_read_input_tokens":1200}
Token使用统计:
  输入tokens: 300
  输出tokens: 150
  总计tokens: 450
  ✅ 缓存读取tokens: 1200 (节省约90%成本!)
  💰 估算节省: ~1080 标准输入tokens的成本
```

**Azure输出**:
```
Token使用统计:
  输入tokens: 1500
  输出tokens: 150
  总计tokens: 1650
  ✅ 缓存命中tokens: 1200 (节省约90%成本!)
  Azure详细信息:
    - cached_tokens: 1200
```

### 未命中缓存 ⚠️

```
Token使用统计:
  输入tokens: 500
  输出tokens: 150
  总计tokens: 650
  ℹ️  未使用缓存 (可能是内容不足1024 tokens或首次请求)
```

## 常见测试问题

### 问题1: 内容不足1024 tokens

**症状**: 日志显示 `未使用缓存`

**解决**:
- 检查日志中的 `估算tokens` 值
- 确保超过1024 (Bedrock某些模型需要2048-4096)
- 测试类已包含足够长的内容

### 问题2: Bedrock认证失败

**症状**: 403或认证错误

**解决**:
```bash
# 检查AWS凭证
aws sts get-caller-identity

# 或显式传递凭证
mvn test -Dtest=BedrockPromptCachingTest \
  -Daws.accessKeyId=xxx \
  -Daws.secretAccessKey=xxx \
  -Dbedrock.region=us-east-1
```

### 问题3: Azure API版本不支持

**症状**: 响应中没有cached_tokens字段

**解决**:
- 确保使用 `2024-10-01-preview` 或更新版本
- 检查日志中的 `API版本:` 输出
- 更新系统属性: `-Dazure.apiVersion=2024-10-01-preview`

### 问题4: 模型不支持缓存

**Bedrock支持的模型**:
- Claude 3.7 Sonnet (1024 tokens最小)
- Claude 3.5 Haiku (2048 tokens最小)
- Claude Haiku 4.5 (4096 tokens最小)

**Azure支持的模型**:
- gpt-4o (2024-08-06+)
- gpt-4o-mini
- o1 系列 (2024-12-17+)
- o3-mini (2025-01-31)

## 成本计算器

### Bedrock (Claude 3.7 Sonnet)
假设价格: $0.003/1K input tokens

```
标准请求(10K tokens): $0.030
缓存创建(10K tokens): $0.0375 (+25%)
缓存读取(10K tokens): $0.003 (-90%)

10次请求总成本:
- 无缓存: $0.300
- 使用缓存: $0.0675 (节省78%)
```

### Azure (gpt-4o)
假设价格: $0.005/1K input tokens

```
标准请求(10K tokens): $0.050
缓存读取(10K tokens, Standard): $0.005 (-90%)
缓存读取(10K tokens, Provisioned): $0.000 (-100%)

10次请求总成本:
- 无缓存: $0.500
- Standard缓存: $0.095 (节省81%)
- Provisioned缓存: $0.050 (节省90%)
```

## 最佳实践

### 1. 内容组织
```java
// ✅ 好的做法 - 固定内容在前
ChatMessage.system("长系统提示[固定,1024+ tokens]")
ChatMessage.user("变化的问题")

// ❌ 不好 - 变化内容在前
ChatMessage.user("变化的问题")
ChatMessage.system("长系统提示")
```

### 2. 缓存策略

**Bedrock - 精细控制**:
```java
// 方式1: System缓存
request.setBedrockEnableSystemCache(true);

// 方式2: Content缓存
ChatMessage.user(
    ContentPart.textWithCache(fixedContext, true),
    ContentPart.text(dynamicQuestion)
)
```

**Azure - 自动优化**:
```java
// 只需保持前缀一致
// Azure自动检测并缓存重复部分
request.setMessages(Arrays.asList(
    ChatMessage.system(sameSystemPrompt),
    ChatMessage.user(differentQuestion)
));
```

### 3. 时间窗口

- ⏱️ 缓存有效期: 5-10分钟
- ✅ 在有效期内重复使用
- ❌ 避免长时间间隔

### 4. 监控和调试

```java
// 打印原始响应(Bedrock)
logger.info("原始usage: {}", usageNode.toString());

// 检查Azure详细信息
if (usage.getPromptTokensDetails() != null) {
    logger.info("cached_tokens: {}",
        usage.getPromptTokensDetails().getCachedTokens());
}
```

## 相关文档

- [Bedrock详细指南](BEDROCK_PROMPT_CACHING.md)
- [Azure详细指南](AZURE_PROMPT_CACHING.md)
- [Azure测试说明](AZURE_PROMPT_CACHING_TEST.md)
- [缓存行为解释](CACHE_BEHAVIOR_EXPLAINED.md)
- [功能总结对比](PROMPT_CACHING_SUMMARY.md)

## 下一步

1. **运行测试**: 选择Bedrock或Azure测试运行
2. **查看输出**: 观察缓存统计和原始响应
3. **调整内容**: 根据需要修改prompt长度
4. **集成到项目**: 参考测试代码实现缓存功能

---

**提示**: 如果测试遇到问题,请先查看 [CACHE_BEHAVIOR_EXPLAINED.md](CACHE_BEHAVIOR_EXPLAINED.md) 了解缓存的工作原理和常见问题。
