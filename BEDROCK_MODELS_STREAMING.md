# Bedrock Models Streaming and Usage Information

## Overview

This document describes how different Bedrock models handle streaming responses and usage (token count) information.

## Model-Specific Implementation

### 1. Claude (Anthropic)

**Streaming Support**: ✅ Yes

**Usage Information**:
- Provided through streaming events:
  - `message_start`: Contains initial `input_tokens`
  - `message_delta`: Contains cumulative usage with both `input_tokens` and `output_tokens`
- Final usage should be taken from the last `message_delta` event

**Event Format**:
```json
// message_start
{
  "type": "message_start",
  "message": {
    "usage": {
      "input_tokens": 25,
      "output_tokens": 1
    }
  }
}

// message_delta (cumulative)
{
  "type": "message_delta",
  "usage": {
    "input_tokens": 25,
    "output_tokens": 15
  }
}
```

### 2. Titan (Amazon)

**Streaming Support**: ✅ Yes

**Usage Information**:
- `inputTextTokenCount`: Number of input tokens
- `totalOutputTextTokenCount`: Total number of output tokens (cumulative in streaming)
- These fields may appear in any chunk during streaming

**Response Format**:
```json
{
  "outputText": "Generated text...",
  "inputTextTokenCount": 10,
  "totalOutputTextTokenCount": 25,
  "completionReason": "FINISH"
}
```

### 3. Llama (Meta)

**Streaming Support**: ❌ No
- Llama models on Bedrock do not support streaming responses
- Usage information is not provided in the response

### 4. Cohere

**Streaming Support**: ✅ Yes

**Usage Information**:
- Token count information is provided in HTTP headers, not in the response body
- Response body does not include usage information
- Streaming is supported for Command models

**Request Format**:
- Command R/R+: Uses `message` field
- Command (older): Uses `prompt` field

**Response Format**:
```json
// Command R/R+ Response
{
  "text": "Generated text...",
  "finish_reason": "COMPLETE"
}

// Command (older) Response
{
  "generations": [{
    "text": "Generated text...",
    "finish_reason": "COMPLETE"
  }]
}
```

### 5. Jurassic (AI21)

**Streaming Support**: ❌ No
- Jurassic models do not support streaming responses
- All responses are returned as complete generations

**Usage Information**:
- Provided in the response body
- `prompt.tokens`: Array of input tokens (count = array length)
- `completions[0].data.tokens`: Array of output tokens (count = array length)

**Response Format**:
```json
{
  "id": 1234,
  "prompt": {
    "text": "User prompt",
    "tokens": [...]  // Array of token IDs
  },
  "completions": [{
    "data": {
      "text": "Generated text...",
      "tokens": [...]  // Array of token IDs
    },
    "finishReason": "endOfText"
  }]
}
```

## Stream Options Support

All models that support streaming should handle the `stream_options` parameter:

```java
ChatCompletionRequest.StreamOptions streamOptions = new ChatCompletionRequest.StreamOptions(true);
request.setStreamOptions(streamOptions);
```

When `include_usage` is true, the adapter should ensure usage information is included in the streaming chunks where available.

## Implementation Guidelines

1. **Check Model Support**: Before attempting streaming, verify the model supports it
2. **Handle Usage Accumulation**: For models that provide incremental usage, accumulate properly
3. **Error Handling**: Gracefully handle chunks that don't parse correctly
4. **Consistent Format**: Always convert to OpenAI's standardized format for usage:
   ```java
   ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
   usage.setPromptTokens(inputTokens);
   usage.setCompletionTokens(outputTokens);
   usage.setTotalTokens(inputTokens + outputTokens);
   ```

## Testing

When testing streaming with usage information:

1. Set `stream_options` with `include_usage: true`
2. Accumulate usage information across all chunks
3. Verify the final usage matches non-streaming response (where applicable)
4. Handle models that don't provide usage information gracefully

## Notes

- Not all models provide token usage information
- Streaming format varies significantly between model providers
- Some models may provide usage only in the final chunk
- Always check for null values before accessing usage fields