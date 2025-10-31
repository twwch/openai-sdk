# Azure OpenAI Prompt Caching 测试指南

## 概述

`AzurePromptCachingTest` 是一个完整的测试类,用于验证Azure OpenAI的自动prompt caching功能。

## 前置条件

### 1. Azure OpenAI资源

确保你有:
- ✅ Azure OpenAI资源(Resource)
- ✅ 部署的支持缓存的模型(Deployment)
- ✅ API密钥(API Key)

### 2. 支持的模型

Prompt caching支持以下模型:
- `gpt-4o` (2024-08-06或更新)
- `gpt-4o-mini`
- `o1` 系列 (2024-12-17或更新)
- `o3-mini` (2025-01-31)

### 3. API版本要求

**必须使用 `2024-10-01-preview` 或更新的API版本**

## 运行测试

### 方式1: 使用系统属性

```bash
mvn test -Dtest=AzurePromptCachingTest \
  -Dazure.apiKey=YOUR_API_KEY \
  -Dazure.resourceName=YOUR_RESOURCE_NAME \
  -Dazure.deploymentId=YOUR_DEPLOYMENT_ID \
  -Dazure.apiVersion=2024-10-01-preview
```

### 方式2: 使用环境变量

```bash
# 设置环境变量
export AZURE_OPENAI_API_KEY="your-api-key"
export AZURE_OPENAI_RESOURCE="your-resource-name"
export AZURE_OPENAI_DEPLOYMENT="your-deployment-id"

# 运行测试
mvn test -Dtest=AzurePromptCachingTest
```

### 运行单个测试

```bash
# 测试1: 基本缓存
mvn test -Dtest=AzurePromptCachingTest#testBasicPromptCaching \
  -Dazure.apiKey=xxx \
  -Dazure.resourceName=xxx \
  -Dazure.deploymentId=xxx

# 测试2: 流式缓存
mvn test -Dtest=AzurePromptCachingTest#testStreamingWithCaching \
  -Dazure.apiKey=xxx \
  -Dazure.resourceName=xxx \
  -Dazure.deploymentId=xxx

# 测试3: 多轮对话缓存
mvn test -Dtest=AzurePromptCachingTest#testMultiTurnConversationCaching \
  -Dazure.apiKey=xxx \
  -Dazure.resourceName=xxx \
  -Dazure.deploymentId=xxx
```

## 测试说明

### 测试1: `testBasicPromptCaching()`

**目的**: 验证Azure自动缓存基本功能

**场景**:
1. 第1次请求 - 使用长system prompt (1024+ tokens)
2. 第2次请求 - 相同的system prompt,不同的用户问题
3. Azure自动检测重复内容并缓存

**预期结果**:
- 第1次: `cached_tokens = 0` (或很小)
- 第2次: `cached_tokens > 1000` ✅

### 测试2: `testStreamingWithCaching()`

**目的**: 验证流式响应中的缓存功能

**场景**:
1. 流式请求1 - 建立缓存
2. 流式请求2 - 命中缓存
3. 实时输出响应文本
4. 最后显示usage统计

**预期结果**:
- 第2次流式请求应该显示缓存命中
- Usage信息在流结束时返回

### 测试3: `testMultiTurnConversationCaching()`

**目的**: 验证多轮对话中的缓存

**场景**:
1. 构建包含多轮对话的历史
2. 第1次请求 - 历史+新问题
3. 第2次请求 - 相同历史+不同新问题
4. Azure缓存历史部分

**预期结果**:
- 对话历史部分被缓存
- 只有新问题部分需要重新处理

## 理解输出

### 成功的缓存命中示例

```
=== 第二次请求 (应该命中Azure缓存) ===
回答: 深度学习是机器学习的一个子领域...
Token使用统计:
  输入tokens: 1500
  输出tokens: 150
  总计tokens: 1650
  ✅ 缓存命中tokens: 1200 (节省约90%成本!)
  💰 估算节省: ~1080 tokens的成本 (约$0.005400 at $0.005/1K for gpt-4o)
  Azure详细信息:
    - cached_tokens: 1200
```

### 未命中缓存的情况

```
Token使用统计:
  输入tokens: 1500
  输出tokens: 150
  总计tokens: 1650
  ℹ️  未命中缓存 (可能是首次请求或内容不足1024 tokens)
```

**可能原因**:
1. ❌ API版本太老(不是2024-10-01-preview+)
2. ❌ 模型不支持缓存
3. ❌ 内容少于1024 tokens
4. ❌ 两次请求间隔太长(>5-10分钟)
5. ❌ 内容不完全相同

## 成本分析

### Standard部署

假设gpt-4o定价: $0.005/1K input tokens

| 场景 | 无缓存成本 | 缓存成本 | 节省 |
|------|----------|----------|------|
| 单次请求(1500 tokens) | $0.0075 | $0.0075 | $0 |
| 第2次(1200 cached) | $0.0075 | $0.0015 | $0.006 (80%) |
| 10次请求 | $0.075 | $0.0165 | $0.0585 (78%) |
| 100次请求 | $0.75 | $0.0825 | $0.6675 (89%) |

### Provisioned部署

缓存token可能**完全免费**(最高100%折扣),具体取决于你的定价协议。

## 常见问题

### Q1: 为什么第一次请求也显示cached_tokens?

**A**: 这是正常的。Azure可能已经缓存了部分内容(如果之前有相似请求),或者显示为0。第二次请求应该显示明显更高的cached_tokens。

### Q2: 如何确认API版本支持缓存?

**A**: 查看日志输出:
```
创建Azure OpenAI客户端:
  资源名: your-resource
  部署ID: gpt-4o
  API版本: 2024-10-01-preview  ← 确认这里
```

### Q3: 测试失败怎么办?

**A**: 检查配置:
```bash
# 1. 验证环境变量
echo $AZURE_OPENAI_API_KEY
echo $AZURE_OPENAI_RESOURCE
echo $AZURE_OPENAI_DEPLOYMENT

# 2. 验证API版本
# 确保是 2024-10-01-preview 或更新

# 3. 验证模型
# 确保部署的是 gpt-4o, gpt-4o-mini, o1 等支持缓存的模型
```

### Q4: 如何最大化缓存效果?

**建议**:
1. ✅ 将固定内容放在消息开头
2. ✅ 确保前1024+ tokens保持不变
3. ✅ 在5-10分钟内重复使用相同前缀
4. ✅ 使用长的system prompt或对话历史

## 完整示例

```bash
# 设置环境变量
export AZURE_OPENAI_API_KEY="abc123def456..."
export AZURE_OPENAI_RESOURCE="my-openai-resource"
export AZURE_OPENAI_DEPLOYMENT="gpt-4o"

# 运行所有测试
mvn test -Dtest=AzurePromptCachingTest

# 或者使用系统属性(推荐用于CI/CD)
mvn test -Dtest=AzurePromptCachingTest \
  -Dazure.apiKey="${AZURE_OPENAI_API_KEY}" \
  -Dazure.resourceName="${AZURE_OPENAI_RESOURCE}" \
  -Dazure.deploymentId="${AZURE_OPENAI_DEPLOYMENT}" \
  -Dazure.apiVersion=2024-10-01-preview
```

## 参考资料

- [Azure OpenAI Prompt Caching官方文档](https://learn.microsoft.com/en-us/azure/ai-foundry/openai/how-to/prompt-caching)
- [SDK文档: AZURE_PROMPT_CACHING.md](AZURE_PROMPT_CACHING.md)
- [缓存行为说明: CACHE_BEHAVIOR_EXPLAINED.md](CACHE_BEHAVIOR_EXPLAINED.md)
- [Bedrock vs Azure对比: PROMPT_CACHING_SUMMARY.md](PROMPT_CACHING_SUMMARY.md)

## 技术支持

如果测试遇到问题:
1. 查看[CACHE_BEHAVIOR_EXPLAINED.md](CACHE_BEHAVIOR_EXPLAINED.md)了解缓存工作原理
2. 检查Azure Portal中的部署配置
3. 验证API版本和模型支持
4. 查看完整的usage对象输出
