# Azure OpenAI Prompt Caching 使用指南

## 概述

Azure OpenAI的Prompt Caching功能**完全自动化**,无需任何代码修改即可享受成本优惠。当使用API版本`2024-10-01-preview`或更新版本时,Azure会自动识别并缓存重复的prompt内容。

## 关键特点

- ✅ **完全自动** - 无需配置,无法禁用
- ✅ **自动检测** - Azure智能识别重复的prompt前缀
- ✅ **90-100%折扣** - Standard部署90%折扣,Provisioned部署100%折扣
- ✅ **即时生效** - 只需使用正确的API版本

## 成本节省

| 部署类型 | 缓存读取折扣 | 说明 |
|---------|------------|------|
| **Standard** | ~90% | 缓存token只需支付约10%的价格 |
| **Provisioned** | 最高100% | 缓存token可能完全免费 |

### 与Bedrock对比

| 特性 | Azure OpenAI | AWS Bedrock |
|------|-------------|-------------|
| 配置方式 | 完全自动 | 需要显式标记cache_control |
| API版本要求 | 2024-10-01-preview+ | 无特殊要求 |
| 缓存控制 | 自动检测 | 精细控制(system/content) |
| 成本节省 | 90-100% | 90% |
| 最小tokens | 1024 | 1024-4096(按模型) |
| 缓存有效期 | 5-10分钟 | 5分钟 |

## 使用方式

### 1. 设置正确的API版本

这是**唯一**需要做的配置:

```java
// 创建Azure OpenAI客户端,使用支持prompt caching的API版本
OpenAI client = OpenAI.azure(
    "your-api-key",
    "your-resource-name",
    "your-deployment-name",
    "2024-10-01-preview"  // ⭐ 关键: 使用2024-10-01-preview或更新版本
);
```

或使用更新的API版本:
```java
OpenAI client = OpenAI.azure(
    apiKey,
    resourceName,
    deploymentId,
    "2025-04-01-preview"  // 最新版本
);
```

### 2. 正常使用 - 无需其他修改

```java
// 创建包含长system prompt的请求
String longSystemPrompt = generateLongSystemPrompt(); // 1024+ tokens

ChatCompletionRequest request = new ChatCompletionRequest();
request.setMessages(Arrays.asList(
    ChatMessage.system(longSystemPrompt),
    ChatMessage.user("用户问题")
));

// 直接调用,Azure会自动处理缓存
ChatCompletionResponse response = client.createChatCompletion(request);

// 检查缓存统计(可选)
if (response.getUsage().getCacheReadInputTokens() != null) {
    int cachedTokens = response.getUsage().getCacheReadInputTokens();
    System.out.println("✅ 缓存命中: " + cachedTokens + " tokens (节省90%!)");
}
```

## 如何确认缓存生效

### 检查响应中的usage字段

```java
ChatCompletionResponse.Usage usage = response.getUsage();

System.out.println("输入tokens: " + usage.getPromptTokens());
System.out.println("输出tokens: " + usage.getCompletionTokens());

// Azure会在响应中包含缓存统计
if (usage.getCacheReadInputTokens() != null && usage.getCacheReadInputTokens() > 0) {
    System.out.println("✅ 缓存命中: " + usage.getCacheReadInputTokens() + " tokens");
    System.out.println("💰 节省约: " + (usage.getCacheReadInputTokens() * 0.9) + " tokens的成本");
}

if (usage.getCacheCreationInputTokens() != null && usage.getCacheCreationInputTokens() > 0) {
    System.out.println("⚠️ 缓存创建: " + usage.getCacheCreationInputTokens() + " tokens");
}
```

## 缓存工作原理

### 自动缓存条件

Azure会自动缓存满足以下条件的内容:

1. **最小长度**: 前1024 tokens必须相同
2. **位置**: 从请求开头开始
3. **连续性**: 每连续128 tokens会创建缓存点

### 示例场景

```java
// 场景1: System消息缓存
// 第一次请求
request1.setMessages(Arrays.asList(
    ChatMessage.system("长系统提示...[1024+ tokens]"),
    ChatMessage.user("问题1")
));
// Azure创建缓存

// 第二次请求(5分钟内)
request2.setMessages(Arrays.asList(
    ChatMessage.system("长系统提示...[相同的1024+ tokens]"),
    ChatMessage.user("问题2")  // 不同的问题
));
// Azure自动命中缓存,节省90%成本
```

```java
// 场景2: 多轮对话缓存
List<ChatMessage> history = new ArrayList<>();
history.add(ChatMessage.system("系统设定"));
history.add(ChatMessage.user("问题1"));
history.add(ChatMessage.assistant("回答1"));
history.add(ChatMessage.user("问题2"));
history.add(ChatMessage.assistant("回答2"));
// ... 更多历史消息

// 如果历史消息总共超过1024 tokens,Azure会缓存这部分
history.add(ChatMessage.user("新问题"));
ChatCompletionRequest request = new ChatCompletionRequest();
request.setMessages(history);
// Azure自动识别并缓存重复的历史部分
```

## 最佳实践

### 1. 将固定内容放在前面

```java
// ✅ 推荐: 固定的长内容在前
List<ChatMessage> messages = Arrays.asList(
    ChatMessage.system("长系统提示...[固定,1024+ tokens]"),
    ChatMessage.user("变化的问题")
);

// ❌ 不推荐: 变化内容在前
List<ChatMessage> messages = Arrays.asList(
    ChatMessage.user("变化的问题"),
    ChatMessage.system("长系统提示...")  // 位置不对,可能影响缓存
);
```

### 2. 保持前缀一致

```java
String systemPrompt = loadSystemPrompt(); // 加载一次,重复使用

// 第1个请求
request1.setMessages(Arrays.asList(
    ChatMessage.system(systemPrompt),
    ChatMessage.user("问题A")
));

// 第2个请求 - 保持system不变
request2.setMessages(Arrays.asList(
    ChatMessage.system(systemPrompt),  // 完全相同
    ChatMessage.user("问题B")
));

// 第3个请求
request3.setMessages(Arrays.asList(
    ChatMessage.system(systemPrompt),  // 完全相同
    ChatMessage.user("问题C")
));
```

### 3. 监控缓存效果

```java
public class CacheMonitor {
    private int totalRequests = 0;
    private int cacheHits = 0;
    private int totalCachedTokens = 0;

    public void trackResponse(ChatCompletionResponse response) {
        totalRequests++;

        Integer cached = response.getUsage().getCacheReadInputTokens();
        if (cached != null && cached > 0) {
            cacheHits++;
            totalCachedTokens += cached;

            System.out.printf("缓存命中率: %.1f%% (%d/%d)\n",
                (cacheHits * 100.0 / totalRequests), cacheHits, totalRequests);
            System.out.printf("累计节省: ~%d tokens的成本\n",
                (int)(totalCachedTokens * 0.9));
        }
    }
}
```

### 4. 针对不同场景优化

**RAG (检索增强生成)**
```java
// 将检索到的文档作为system消息(如果重复检索相同文档)
String retrievedDocs = retrieveDocuments(query);

request.setMessages(Arrays.asList(
    ChatMessage.system("你是助手。以下是参考文档:\n\n" + retrievedDocs),
    ChatMessage.user(query)
));
```

**长对话历史**
```java
// 保持对话历史在前,只修改最后的用户消息
List<ChatMessage> conversation = loadConversationHistory(); // 长历史
conversation.add(ChatMessage.user(newQuestion));

request.setMessages(conversation);
// Azure会缓存稳定的历史部分
```

**代码审查/文档分析**
```java
// 将待分析的代码/文档作为system消息
String codeToReview = loadCode();

request.setMessages(Arrays.asList(
    ChatMessage.system("请审查以下代码:\n\n" + codeToReview),
    ChatMessage.user("检查安全问题")
));
```

## 常见问题

### Q1: 如何知道Azure是否支持我的模型?

支持的模型包括:
- gpt-4o (2024-08-06及更新)
- gpt-4o-mini
- o1 系列 (2024-12-17及更新)
- o3-mini (2025-01-31)

查看[官方文档](https://learn.microsoft.com/en-us/azure/ai-foundry/openai/how-to/prompt-caching)获取最新支持列表。

### Q2: 为什么没有看到缓存统计?

可能原因:
1. API版本太老,升级到`2024-10-01-preview`或更新
2. 内容少于1024 tokens
3. 内容每次都在变化
4. 缓存已过期(超过5-10分钟)

### Q3: 可以手动控制缓存吗?

不可以。Azure的prompt caching是完全自动的,无法手动控制。如果需要精细控制,考虑使用AWS Bedrock。

### Q4: Provisioned部署真的免费吗?

Provisioned部署的缓存token可享受**最高100%**折扣,具体取决于你的定价协议。查看Azure账单了解实际优惠。

### Q5: 如何最大化缓存效果?

关键策略:
- 确保前1024+ tokens保持不变
- 将固定内容放在消息开头
- 在5-10分钟内重复使用相同的前缀
- 监控usage统计,优化prompt结构

## 完整示例

```java
import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.*;

public class AzureCachingExample {
    public static void main(String[] args) {
        // 1. 创建客户端,使用支持缓存的API版本
        OpenAI client = OpenAI.azure(
            System.getenv("AZURE_OPENAI_API_KEY"),
            "your-resource-name",
            "gpt-4o-deployment",
            "2024-10-01-preview"  // 支持缓存的版本
        );

        // 2. 准备长system prompt (1024+ tokens)
        String systemPrompt = """
            你是一个专业的AI助手...
            [这里添加足够的内容以达到1024+ tokens]
            """;

        // 3. 第一次请求 - 创建缓存
        System.out.println("=== 第一次请求 ===");
        ChatCompletionRequest req1 = new ChatCompletionRequest();
        req1.setMessages(Arrays.asList(
            ChatMessage.system(systemPrompt),
            ChatMessage.user("什么是人工智能?")
        ));

        ChatCompletionResponse res1 = client.createChatCompletion(req1);
        System.out.println("回答: " + res1.getContent());
        printCacheStats(res1.getUsage());

        // 4. 第二次请求 - 命中缓存
        System.out.println("\n=== 第二次请求 ===");
        ChatCompletionRequest req2 = new ChatCompletionRequest();
        req2.setMessages(Arrays.asList(
            ChatMessage.system(systemPrompt),  // 相同的system
            ChatMessage.user("什么是机器学习?") // 不同的问题
        ));

        ChatCompletionResponse res2 = client.createChatCompletion(req2);
        System.out.println("回答: " + res2.getContent());
        printCacheStats(res2.getUsage());

        client.close();
    }

    private static void printCacheStats(ChatCompletionResponse.Usage usage) {
        System.out.println("Token统计:");
        System.out.println("  输入: " + usage.getPromptTokens());
        System.out.println("  输出: " + usage.getCompletionTokens());

        if (usage.getCacheCreationInputTokens() != null) {
            System.out.println("  ⚠️ 创建缓存: " + usage.getCacheCreationInputTokens());
        }

        if (usage.getCacheReadInputTokens() != null && usage.getCacheReadInputTokens() > 0) {
            int cached = usage.getCacheReadInputTokens();
            System.out.println("  ✅ 缓存命中: " + cached);
            System.out.println("  💰 估算节省: ~" + (cached * 0.9) + " tokens成本");
        }
    }
}
```

## 参考资料

- [Azure OpenAI Prompt Caching 官方文档](https://learn.microsoft.com/en-us/azure/ai-foundry/openai/how-to/prompt-caching)
- [Azure OpenAI 定价](https://azure.microsoft.com/en-us/pricing/details/cognitive-services/openai-service/)
- [API 版本发布说明](https://learn.microsoft.com/en-us/azure/ai-services/openai/api-version-deprecation)

## 总结

Azure OpenAI的Prompt Caching功能极其简单:

1. ✅ 使用`2024-10-01-preview`或更新的API版本
2. ✅ 确保重复内容超过1024 tokens
3. ✅ 将固定内容放在消息开头
4. ✅ 在5-10分钟内重复使用

**就这么简单!** Azure会自动处理剩下的一切,你只需享受90-100%的成本节省。
