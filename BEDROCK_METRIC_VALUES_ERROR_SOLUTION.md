# Bedrock METRIC_VALUES Error 解决方案

## 错误描述
```
io.github.twwch.openai.sdk.exception.OpenAIException: Bedrock流式请求失败: METRIC_VALUES
```

## 问题原因

`METRIC_VALUES`错误的主要原因是**请求包含了Bedrock不支持的OpenAI特有参数**。

具体导致错误的参数包括：
- `n` - Bedrock不支持多个响应
- `logprobs` - Bedrock不支持日志概率
- `presence_penalty` - Bedrock不支持存在惩罚
- `frequency_penalty` - Bedrock不支持频率惩罚
- `service_tier` - Bedrock特有的服务层级概念

即使是一个简单的126字节请求也会因为包含这些参数而失败。

## 解决方案

### 1. 限制工具数量
在`ClaudeModelAdapter`中添加了工具数量限制（最多20个）：
```java
final int MAX_TOOLS = 20; // 限制工具数量，避免请求过大
```

### 2. 清理不支持的参数
在发送请求前，清除以下OpenAI特有参数：
- `logprobs`
- `service_tier`
- `parallel_tool_calls`
- `presence_penalty`
- `frequency_penalty`
- `seed`
- `top_logprobs`
- `store`

### 3. 避免发送默认值
只在参数值不等于默认值时才发送：
```java
// 只有在temperature不为null且不等于默认值1.0时才设置
if (request.getTemperature() != null && !request.getTemperature().equals(1.0)) {
    bedrockRequest.put("temperature", request.getTemperature());
}
```

### 4. 调试建议

使用`BedrockDebugDemo.java`进行调试：
1. 测试无工具的简单请求
2. 测试少量工具的请求
3. 逐步增加复杂度，定位问题

### 5. 最佳实践

1. **工具管理**：
   - 只包含必要的工具
   - 考虑动态选择相关工具而不是一次性发送所有工具

2. **参数清理**：
   ```java
   // 清除所有可能导致问题的参数
   request.setPresencePenalty(null);
   request.setFrequencyPenalty(null);
   request.setLogprobs(null);
   request.setServiceTier(null);
   request.setParallelToolCalls(null);
   ```

3. **错误处理**：
   - 添加请求大小检查
   - 提供有意义的错误信息
   - 记录调试信息

## 监控和日志

添加了以下调试信息：
- 请求体大小：`Bedrock流式请求长度: X bytes`
- 工具数量：`添加了 X 个工具到请求中`
- 大小警告：`警告：请求体过大，可能超出限制`

## 参考资料

- AWS Bedrock请求大小限制：20MB
- Claude模型需要Anthropic Claude 3或更高版本才支持工具使用
- 建议使用AWS Service Quotas控制台查看具体限制