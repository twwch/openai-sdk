# AWS Bedrock Implementation Notes

## Stream Options and Usage Information

### Key Findings

1. **Stream Options Support**
   - Bedrock支持`stream_options`参数，可以在请求中设置：
   ```java
   ChatCompletionRequest.StreamOptions streamOptions = new ChatCompletionRequest.StreamOptions(true);
   request.setStreamOptions(streamOptions);
   ```

2. **Usage Information in Streaming**
   - Bedrock通过不同的事件返回usage信息：
     - `message_start`事件：包含初始的`input_tokens`
     - `message_delta`事件：包含累积的usage信息（`input_tokens`和`output_tokens`）
   - 最终的usage信息应该从`message_delta`事件中获取

3. **Event Structure**
   ```json
   // message_start事件
   {
     "type": "message_start",
     "message": {
       "usage": {
         "input_tokens": 25,
         "output_tokens": 1
       }
     }
   }
   
   // message_delta事件（累积的usage）
   {
     "type": "message_delta",
     "usage": {
       "input_tokens": 25,
       "output_tokens": 15
     }
   }
   ```

## Tool Calling Implementation

### 请求格式差异

1. **OpenAI格式**：
   ```json
   {
     "tools": [{
       "type": "function",
       "function": {
         "name": "get_weather",
         "description": "获取天气",
         "parameters": {...}
       }
     }]
   }
   ```

2. **Bedrock/Claude格式**：
   ```json
   {
     "tools": [{
       "name": "get_weather",
       "description": "获取天气",
       "input_schema": {...}
     }]
   }
   ```

### 工具消息角色转换

- OpenAI使用`tool`角色，Bedrock不支持
- 需要转换为`user`角色，内容类型为`tool_result`：

```java
// OpenAI格式
ChatMessage.tool(toolCallId, result)

// 转换为Bedrock格式
{
  "role": "user",
  "content": [{
    "type": "tool_result",
    "tool_use_id": toolCallId,
    "content": result
  }]
}
```

### Tool Choice参数

- 只有在提供tools时才能设置tool_choice
- Bedrock使用对象格式：
  - `{"type": "auto"}` - 自动选择
  - `{"type": "none"}` - 不使用工具
  - `{"type": "tool", "name": "tool_name"}` - 指定工具

## 实现细节

1. **ClaudeModelAdapter**已更新以处理：
   - 工具定义格式转换（`parameters` → `input_schema`）
   - 工具消息角色转换（`tool` → `user` with `tool_result`）
   - 流式响应中的累积usage信息

2. **测试示例**已更新：
   - BedrockToolCallDemo.java - 展示详细的chunk信息
   - BedrockToolCallDemoFixed.java - 使用标准API工具调用

## 注意事项

1. 流式响应中，最终的usage信息来自`message_delta`事件
2. 工具调用需要正确的格式转换才能在Bedrock上工作
3. 不要在请求中包含不必要的默认值（如temperature=1.0等）