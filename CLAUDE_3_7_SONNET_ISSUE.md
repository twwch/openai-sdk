# Claude 3.7 Sonnet 模型问题

## 错误原因

`METRIC_VALUES`错误可能与Claude 3.7 Sonnet的新模型ID格式有关。

### 模型ID问题

您使用的模型ID: `us.anthropic.claude-3-7-sonnet-20250219-v1:0`

这是一个**推理配置文件（Inference Profile）**，而不是基础模型ID。

### 已知问题

根据GitHub和StackOverflow的报告：
- "Invocation of model ID anthropic.claude-3-7-sonnet-20250219-v1:0 with on-demand throughput isn't supported"
- 该模型可能只支持跨区域模式，不支持标准的on-demand调用

## 解决方案

### 1. 使用其他Claude模型

尝试使用稳定的Claude模型：
```java
// Claude 3 Sonnet
String modelId = "anthropic.claude-3-sonnet-20240229";

// Claude 3 Haiku
String modelId = "anthropic.claude-3-haiku-20240307-v1:0";

// Claude 3 Opus
String modelId = "anthropic.claude-3-opus-20240229-v1:0";

// Claude 3.5 Sonnet
String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";
```

### 2. 使用区域特定的端点

如果必须使用Claude 3.7 Sonnet，可能需要：
- 使用特定区域而不是推理配置文件
- 配置跨区域推理支持
- 等待AWS修复此问题

### 3. 测试代码

```java
// 测试不同的模型
String[] modelIds = {
    "anthropic.claude-3-sonnet-20240229",
    "anthropic.claude-3-haiku-20240307-v1:0",
    "anthropic.claude-3-5-sonnet-20240620-v1:0"
};

for (String modelId : modelIds) {
    try {
        OpenAI client = OpenAI.bedrock(region, bearerKey, bearerToken, modelId);
        // 测试请求
    } catch (Exception e) {
        System.err.println("模型 " + modelId + " 失败: " + e.getMessage());
    }
}
```

## 参考资料

- [GitHub Issue #1952](https://github.com/cline/cline/issues/1952)
- [StackOverflow问题](https://stackoverflow.com/questions/79428475/)
- AWS Bedrock支持的模型列表

## 建议

在AWS修复此问题之前，建议使用其他稳定的Claude模型版本。