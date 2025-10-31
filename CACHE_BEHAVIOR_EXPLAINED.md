# Prompt Caching 行为详解

## 理解缓存统计字段

当你使用Bedrock或Azure的prompt caching时，响应中的`usage`对象会包含以下字段：

### 字段说明

1. **promptTokens** - 总输入tokens (包括缓存和非缓存部分)
2. **completionTokens** - 生成的输出tokens
3. **cacheCreationInputTokens** - 首次创建缓存的tokens (成本125%)
4. **cacheReadInputTokens** - 从缓存读取的tokens (成本10%)

### 典型场景

#### 场景1: 第一次请求 (创建缓存)

```
输入tokens: 5000
输出tokens: 500
缓存创建tokens: 4000  ← 标记为需要缓存的部分
缓存读取tokens: 0     ← 没有命中

成本计算:
- 非缓存部分: 1000 tokens × $0.003 = $0.003
- 缓存创建部分: 4000 tokens × $0.003 × 1.25 = $0.015
- 输出: 500 tokens × $0.015 = $0.0075
总计: $0.0255
```

#### 场景2: 第二次请求 (命中缓存)

```
输入tokens: 5000
输出tokens: 500
缓存创建tokens: 0     ← 不需要创建
缓存读取tokens: 4000  ← 从缓存读取

成本计算:
- 非缓存部分: 1000 tokens × $0.003 = $0.003
- 缓存读取部分: 4000 tokens × $0.003 × 0.1 = $0.0012
- 输出: 500 tokens × $0.015 = $0.0075
总计: $0.0117

节省: $0.0255 - $0.0117 = $0.0138 (54%)
```

#### 场景3: 缓存过期后

如果超过5分钟没有使用，缓存会过期，下次请求会重新创建缓存。

## 常见问题

### Q: 为什么第一次请求显示"缓存创建"而不是"缓存命中"？

**A:** 这是正常的！第一次请求时：
- Bedrock会创建缓存 → `cacheCreationInputTokens > 0`
- 还没有可用的缓存 → `cacheReadInputTokens = 0`
- 成本会略高(125%)，但为后续请求做准备

### Q: 什么时候会同时有"创建"和"读取"？

**A:** 通常不会同时出现。但在以下情况可能同时存在：
- 部分内容已缓存(读取)
- 新增内容需要缓存(创建)

例如：
```java
// 第一次: 缓存A
request1.setMessages(Arrays.asList(
    ChatMessage.system("内容A [1024 tokens]"),
    ChatMessage.user("问题1")
));
// 结果: cacheCreation=1024, cacheRead=0

// 第二次: 读取A, 创建B
request2.setMessages(Arrays.asList(
    ChatMessage.system("内容A [1024 tokens]" + "内容B [1024 tokens]"),
    ChatMessage.user("问题2")
));
// 结果: cacheCreation=1024 (B), cacheRead=1024 (A)
```

### Q: 为什么我看到"缓存命中"但日志还打印"缓存创建"？

**A:** 如果你在日志中同时看到两条消息，可能是：

1. **代码中有两个日志输出点**：
   - ClaudeModelAdapter.java:448 会在解析响应时打印
   - BedrockPromptCachingTest.java:227 会在测试中打印

2. **检查实际的usage值**：
   ```java
   System.out.println("创建: " + usage.getCacheCreationInputTokens());
   System.out.println("读取: " + usage.getCacheReadInputTokens());
   ```

3. **可能的原因**：
   - 如果两个值都>0，说明部分命中+部分创建
   - 如果只有读取>0，说明完全命中缓存
   - 如果只有创建>0，说明首次创建缓存

### Q: 如何验证缓存真的工作了？

**A:** 运行两次相同的请求，观察成本变化：

```java
// 第1次
ChatCompletionResponse res1 = client.createChatCompletion(request);
// 应该看到: cacheCreation > 0, cacheRead = 0

Thread.sleep(1000); // 等待一下

// 第2次 (相同的请求)
ChatCompletionResponse res2 = client.createChatCompletion(request);
// 应该看到: cacheCreation = 0, cacheRead > 0 ✅

// 成本对比
double cost1 = calculateCost(res1.getUsage());
double cost2 = calculateCost(res2.getUsage());
System.out.println("第1次成本: $" + cost1);
System.out.println("第2次成本: $" + cost2);
System.out.println("节省: " + ((cost1 - cost2) / cost1 * 100) + "%");
```

## 测试示例输出

### 正常的缓存工作流程

```
=== 第一次请求 (创建缓存) ===
Token使用统计:
  输入tokens: 5120
  输出tokens: 256
  总计tokens: 5376
  ⚠️  缓存创建tokens: 4096 (首次创建,成本约为标准的125%)

=== 第二次请求 (应该命中缓存) ===
Token使用统计:
  输入tokens: 5120
  输出tokens: 312
  总计tokens: 5432
  ✅ 缓存读取tokens: 4096 (节省约90%成本!)
  💰 估算节省: ~3686 标准输入tokens的成本 (约$0.011058 at $0.003/1K)
```

## 调试技巧

### 1. 添加详细日志

```java
private void debugCacheUsage(ChatCompletionResponse.Usage usage) {
    System.out.println("=== 缓存调试信息 ===");
    System.out.println("promptTokens: " + usage.getPromptTokens());
    System.out.println("cacheCreationInputTokens: " + usage.getCacheCreationInputTokens());
    System.out.println("cacheReadInputTokens: " + usage.getCacheReadInputTokens());

    if (usage.getCacheCreationInputTokens() != null && usage.getCacheCreationInputTokens() > 0) {
        System.out.println("状态: 正在创建缓存 (首次请求)");
    } else if (usage.getCacheReadInputTokens() != null && usage.getCacheReadInputTokens() > 0) {
        System.out.println("状态: 缓存命中! ✅");
    } else {
        System.out.println("状态: 未使用缓存 (内容可能<1024 tokens)");
    }
}
```

### 2. 验证缓存内容

```java
String systemPrompt = "...你的长提示...";
System.out.println("System prompt长度: " + systemPrompt.length() + " 字符");

// 粗略估算tokens (1个token约等于4个字符)
int estimatedTokens = systemPrompt.length() / 4;
System.out.println("估算tokens: " + estimatedTokens);

if (estimatedTokens < 1024) {
    System.out.println("⚠️ 警告: 内容可能不足1024 tokens,缓存可能不会生效!");
}
```

### 3. 对比请求

```java
public class CacheComparison {
    public static void main(String[] args) {
        OpenAI client = OpenAI.bedrock("us-east-1", MODEL_ID);

        ChatCompletionRequest request = createRequest(); // 相同的请求

        // 第1次
        ChatCompletionResponse res1 = client.createChatCompletion(request);
        System.out.println("第1次 - 创建: " + res1.getUsage().getCacheCreationInputTokens());
        System.out.println("第1次 - 读取: " + res1.getUsage().getCacheReadInputTokens());

        // 第2次 (1秒后)
        Thread.sleep(1000);
        ChatCompletionResponse res2 = client.createChatCompletion(request);
        System.out.println("第2次 - 创建: " + res2.getUsage().getCacheCreationInputTokens());
        System.out.println("第2次 - 读取: " + res2.getUsage().getCacheReadInputTokens());

        // 验证
        if (res2.getUsage().getCacheReadInputTokens() > 0) {
            System.out.println("✅ 缓存工作正常!");
        } else {
            System.out.println("❌ 缓存未命中,检查内容是否相同");
        }
    }
}
```

## 总结

**正常的缓存行为：**

1. **第1次请求**: `cacheCreation > 0`, `cacheRead = 0` → 创建缓存
2. **第2次请求**: `cacheCreation = 0`, `cacheRead > 0` → 命中缓存 ✅
3. **第3次请求**: `cacheCreation = 0`, `cacheRead > 0` → 继续命中 ✅
4. **5分钟后**: 缓存过期，重复步骤1

**如果看到意外的结果：**
- 检查内容是否真的相同
- 确认内容是否≥1024 tokens
- 验证两次请求间隔<5分钟
- 查看完整的usage对象
