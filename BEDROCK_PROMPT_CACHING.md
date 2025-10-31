# AWS Bedrock Prompt Caching 使用指南

## 概述

AWS Bedrock的Prompt Caching功能可以帮助你**节省90%的输入token成本**。当你重复使用相同的上下文(如system prompt、长文档、代码库等)时,Bedrock会缓存这些内容,后续请求只需支付约10%的成本。

## 成本节省

- **缓存读取**: 享受**90%折扣** (只需支付10%的标准输入token价格)
- **缓存写入**: 首次创建缓存时,成本约为标准输入的125%
- **缓存有效期**: 5分钟 (每次命中会重置计时器)

### 成本计算示例

假设标准输入token价格为 $0.003/1K tokens:

| 场景 | Token数 | 标准成本 | 缓存成本 | 节省 |
|------|---------|----------|----------|------|
| 首次请求(创建缓存) | 10,000 | $0.030 | $0.0375 | -$0.0075 |
| 第2次请求(缓存命中) | 10,000 | $0.030 | $0.003 | $0.027 (90%) |
| 第3次请求(缓存命中) | 10,000 | $0.030 | $0.003 | $0.027 (90%) |
| **总计(3次)** | 30,000 | **$0.090** | **$0.0435** | **$0.0465 (52%)** |

**结论**: 即使首次创建缓存有额外成本,从第2次请求开始就能大幅节省,总体节省率随请求次数增加而提高。

## 支持的模型

根据AWS文档,以下Claude模型支持Prompt Caching:

| 模型 | 最小缓存Token数 | 最大缓存检查点数 |
|------|----------------|----------------|
| Claude 3.7 Sonnet | 1,024 | 4 |
| Claude 3.5 Haiku | 2,048 | 4 |
| Claude Haiku 4.5 | 4,096 | 4 |

⚠️ **重要**: 缓存内容必须达到最小token数才会生效!

## 使用方式

### 方式1: 缓存System消息 (推荐用于固定的系统提示)

适用场景:
- 固定的角色设定
- 长规则列表
- 知识库背景信息

```java
ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("anthropic.claude-3-5-sonnet-20241022-v2:0");
request.setBedrockEnableSystemCache(true); // ⭐ 启用system缓存

String longSystemPrompt = "你是一个专业的AI助手...[1024+ tokens的规则]";

request.setMessages(Arrays.asList(
    ChatMessage.system(longSystemPrompt),
    ChatMessage.user("用户问题")
));

ChatCompletionResponse response = client.createChatCompletion(request);

// 检查缓存统计
if (response.getUsage().getCacheCreationInputTokens() != null) {
    System.out.println("创建了缓存: " + response.getUsage().getCacheCreationInputTokens() + " tokens");
}
if (response.getUsage().getCacheReadInputTokens() != null) {
    System.out.println("命中缓存: " + response.getUsage().getCacheReadInputTokens() + " tokens (节省90%!)");
}
```

### 方式2: 缓存用户消息中的特定内容 (精细控制)

适用场景:
- 长文档分析
- 代码审查
- 多轮对话中的上下文

```java
String longContext = "这是一个很长的文档内容...[1024+ tokens]";

ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("anthropic.claude-3-5-sonnet-20241022-v2:0");
request.setMessages(Arrays.asList(
    ChatMessage.user(
        ChatMessage.ContentPart.textWithCache(longContext, true), // ⭐ 启用缓存
        ChatMessage.ContentPart.text("\n\n基于上述内容,回答问题: ...")
    )
));

ChatCompletionResponse response = client.createChatCompletion(request);
```

### 方式3: 组合使用 (最大化节省)

同时缓存system和用户上下文:

```java
ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("anthropic.claude-3-5-sonnet-20241022-v2:0");
request.setBedrockEnableSystemCache(true); // 缓存system

String systemPrompt = "代码审查规则...[1024+ tokens]";
String codeContext = "// 大型代码库\npublic class ...[1024+ tokens]";

request.setMessages(Arrays.asList(
    ChatMessage.system(systemPrompt),
    ChatMessage.user(
        ChatMessage.ContentPart.textWithCache(codeContext, true), // 缓存代码
        ChatMessage.ContentPart.text("\n\n检查代码问题")
    )
));

ChatCompletionResponse response = client.createChatCompletion(request);
```

## 最佳实践

### 1. 确保达到最小token要求

```java
// ❌ 不会生效 - 内容太短
request.setBedrockEnableSystemCache(true);
request.setMessages(Arrays.asList(
    ChatMessage.system("你是AI助手"), // 只有几个tokens
    ChatMessage.user("问题")
));

// ✅ 正确 - 超过1024 tokens
String longPrompt = generateLongSystemPrompt(); // 确保1024+ tokens
request.setBedrockEnableSystemCache(true);
request.setMessages(Arrays.asList(
    ChatMessage.system(longPrompt),
    ChatMessage.user("问题")
));
```

### 2. 将固定内容放在前面

缓存必须从消息开头开始:

```java
// ✅ 正确
ChatMessage.user(
    ChatMessage.ContentPart.textWithCache(fixedContext, true), // 固定内容在前
    ChatMessage.ContentPart.text("变化的问题: " + question)    // 变化内容在后
)

// ❌ 不推荐
ChatMessage.user(
    ChatMessage.ContentPart.text("变化的问题: " + question),
    ChatMessage.ContentPart.textWithCache(fixedContext, true)
)
```

### 3. 重用相同的上下文

```java
String sharedContext = loadLargeDocument(); // 1024+ tokens

// 第一次请求 - 创建缓存
ChatCompletionRequest req1 = createRequestWithCache(sharedContext, "问题1");
ChatCompletionResponse res1 = client.createChatCompletion(req1);

// 第二次请求 - 命中缓存 (5分钟内)
ChatCompletionRequest req2 = createRequestWithCache(sharedContext, "问题2");
ChatCompletionResponse res2 = client.createChatCompletion(req2);

// 第三次请求 - 命中缓存
ChatCompletionRequest req3 = createRequestWithCache(sharedContext, "问题3");
ChatCompletionResponse res3 = client.createChatCompletion(req3);
```

### 4. 监控缓存效果

```java
ChatCompletionResponse response = client.createChatCompletion(request);
ChatCompletionResponse.Usage usage = response.getUsage();

// 打印详细统计
System.out.println("输入tokens: " + usage.getPromptTokens());
System.out.println("输出tokens: " + usage.getCompletionTokens());

if (usage.getCacheCreationInputTokens() != null && usage.getCacheCreationInputTokens() > 0) {
    System.out.println("⚠️ 创建缓存: " + usage.getCacheCreationInputTokens() + " tokens (成本+25%)");
}

if (usage.getCacheReadInputTokens() != null && usage.getCacheReadInputTokens() > 0) {
    System.out.println("✅ 缓存命中: " + usage.getCacheReadInputTokens() + " tokens (节省90%!)");
    double standardCost = usage.getCacheReadInputTokens() * 0.003 / 1000;
    double cachedCost = standardCost * 0.1;
    double savings = standardCost - cachedCost;
    System.out.printf("💰 本次节省: $%.6f\n", savings);
}
```

## 实际应用场景

### 场景1: RAG (检索增强生成)

```java
// 检索到的文档内容
String retrievedDocs = retrieveDocuments(userQuery); // 可能很长

ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("anthropic.claude-3-5-sonnet-20241022-v2:0");
request.setBedrockEnableSystemCache(true);
request.setMessages(Arrays.asList(
    ChatMessage.system("你是一个基于文档回答问题的助手"),
    ChatMessage.user(
        ChatMessage.ContentPart.textWithCache(retrievedDocs, true),
        ChatMessage.ContentPart.text("\n\n问题: " + userQuery)
    )
));
```

### 场景2: 代码审查助手

```java
String codeReviewRules = loadCodeReviewGuidelines(); // 长规则
String codeToReview = getCodeFromPR(); // 大型PR代码

ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("anthropic.claude-3-5-sonnet-20241022-v2:0");
request.setBedrockEnableSystemCache(true);
request.setMessages(Arrays.asList(
    ChatMessage.system(codeReviewRules),
    ChatMessage.user(
        ChatMessage.ContentPart.textWithCache(codeToReview, true),
        ChatMessage.ContentPart.text("\n\n请审查代码")
    )
));
```

### 场景3: 多轮对话

```java
// 对话上下文保持不变,只有最后的问题变化
String conversationContext = buildConversationHistory(); // 历史对话

for (String question : userQuestions) {
    ChatCompletionRequest request = new ChatCompletionRequest();
    request.setModel("anthropic.claude-3-5-sonnet-20241022-v2:0");
    request.setMessages(Arrays.asList(
        ChatMessage.user(
            ChatMessage.ContentPart.textWithCache(conversationContext, true),
            ChatMessage.ContentPart.text("\n\n用户: " + question)
        )
    ));

    ChatCompletionResponse response = client.createChatCompletion(request);
    // 第一次创建缓存,后续命中缓存
}
```

## 注意事项

1. **缓存失效条件**:
   - 5分钟无活动
   - 缓存内容发生任何变化
   - 缓存前的内容发生变化

2. **成本考虑**:
   - 如果只调用1-2次,不建议使用缓存
   - 调用3次以上才能实现整体成本节省
   - 适合高频重复的场景

3. **Token计数**:
   - 使用工具如`tiktoken`来预估token数量
   - 确保缓存内容达到最小要求(1024-4096 tokens)

4. **调试技巧**:
   - 查看`cache_creation_input_tokens`确认缓存已创建
   - 查看`cache_read_input_tokens`确认缓存命中
   - 如果都为0,检查内容是否达到最小token数

## 测试

运行测试查看实际效果:

```bash
mvn test -Dtest=BedrockPromptCachingTest
```

查看日志输出中的缓存统计信息。

## 参考资料

- [AWS Bedrock Prompt Caching文档](https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html)
- [Anthropic Claude Pricing](https://docs.claude.com/en/docs/about-claude/pricing)
- [AWS博客: Prompt Caching最佳实践](https://aws.amazon.com/blogs/machine-learning/effectively-use-prompt-caching-on-amazon-bedrock/)
