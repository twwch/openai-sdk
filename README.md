# OpenAI Java SDK

A Java SDK for interacting with OpenAI, Azure OpenAI, and AWS Bedrock APIs.

## Overview

This SDK provides a simple and intuitive way to integrate AI services into your Java applications. It supports standard OpenAI API, Azure OpenAI Service endpoints, and AWS Bedrock with a unified interface.

## Features

- Easy-to-use API client for OpenAI services
- Support for OpenAI, Azure OpenAI, and AWS Bedrock endpoints
- Chat completion API support
- Streaming responses support
- Function calling and tool use support
- Configurable HTTP client
- Comprehensive error handling
- Type-safe request/response models
- Simplified API methods for common use cases
- Automatic format conversion between different AI providers

## Requirements

- Java 11 or higher
- Maven 3.6 or higher

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.twwch</groupId>
    <artifactId>openai-sdk</artifactId>
    <version>1.1.51</version>
</dependency>
```

## Quick Start

### Basic Chat Example

```java
import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.OpenAIConfig;
import io.github.twwch.openai.sdk.model.chat.*;

public class Example {
    public static void main(String[] args) {
        // Configure OpenAI client
        OpenAI openai = new OpenAI("your-api-key");
        
        // Simple chat
        String response = openai.chat("gpt-3.5-turbo", "Hello, how are you?");
        System.out.println(response);
        
        // Chat with system prompt
        String response2 = openai.chat("gpt-3.5-turbo", 
            "You are a helpful assistant.", 
            "What is the capital of France?");
        System.out.println(response2);
    }
}
```

### Azure OpenAI Example

```java
import io.github.twwch.openai.sdk.OpenAI;

public class AzureExample {
    public static void main(String[] args) {
        // Configure Azure OpenAI client
        OpenAI openai = OpenAI.azure(
            "your-azure-api-key",
            "your-resource-name",
            "your-deployment-id"
        );
        
        // Usage is the same as standard OpenAI
        String response = openai.chat("gpt-35-turbo", "Tell me a joke");
        System.out.println(response);
    }
}
```

### AWS Bedrock Example

AWS Bedrock支持两种认证方式：标准AWS凭证和Bedrock API Key。

#### 方式1：使用标准AWS凭证（IAM用户）

```java
import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import java.util.Arrays;

public class BedrockStandardCredentialsExample {
    public static void main(String[] args) {
        // 使用标准AWS凭证（Access Key ID + Secret Access Key）
        OpenAI openai = OpenAI.bedrock(
            "us-east-1",                              // 区域
            "AKIAIOSFODNN7EXAMPLE",                   // AWS Access Key ID
            "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", // AWS Secret Access Key
            "anthropic.claude-3-sonnet-20240229"      // 模型ID
        );
        
        // 简单对话
        String response = openai.chat("claude", "What is AWS Bedrock?");
        System.out.println(response);
        
        // 流式对话
        openai.chatStream("claude", "Write a haiku about cloud computing", 
            chunk -> System.out.print(chunk)
        );
        
        // 高级用法 - 完整的请求配置
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(Arrays.asList(
            ChatMessage.system("You are a helpful assistant"),
            ChatMessage.user("Explain quantum computing in simple terms")
        ));
        request.setMaxTokens(200);
        request.setTemperature(0.7);
        
        ChatCompletionResponse response2 = openai.createChatCompletion(request);
        System.out.println(response2.getContent());
    }
}
```

#### 方式2：使用Bedrock API Key（推荐）

```java
import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.*;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class BedrockApiKeyExample {
    public static void main(String[] args) throws InterruptedException {
        // 使用Bedrock API Key格式的凭证
        // 这种方式专门为Bedrock设计，避免与其他AWS服务凭证冲突
        OpenAI openai = OpenAI.bedrock(
            "us-east-2",                              // 区域
            "BedrockAPIKey-c1cg-at-448479377682",    // Bedrock API Key
            "ABSKQmVkcm9ja0FQSS1jMWNnLWF0LTQ0ODQ3OTM3NzY4MgBlYTFmNGE5Yi00YjI3LTRjZDktOGJiYy05NDQ0M2I0OWZmYTY=", // Session Token
            "anthropic.claude-3-5-sonnet-20240620-v1:0" // 模型ID
        );
        
        // 示例1：简单对话
        System.out.println("=== 简单对话示例 ===");
        String response = openai.chat("claude", "你好，请用中文回复");
        System.out.println(response);
        
        // 示例2：带系统提示的对话
        System.out.println("\n=== 系统提示示例 ===");
        String response2 = openai.chat("claude", 
            "你是一个专业的技术文档编写助手", 
            "请解释什么是微服务架构"
        );
        System.out.println(response2);
        
        // 示例3：流式响应（实时输出）
        System.out.println("\n=== 流式响应示例 ===");
        CountDownLatch latch = new CountDownLatch(1);
        
        openai.chatStream("claude", 
            "写一首关于人工智能的五言绝句", 
            chunk -> System.out.print(chunk),      // 实时打印每个字符
            () -> {                                // 完成回调
                System.out.println("\n[流式响应完成]");
                latch.countDown();
            },
            error -> {                             // 错误处理
                System.err.println("错误: " + error.getMessage());
                latch.countDown();
            }
        );
        
        latch.await(); // 等待流式响应完成
        
        // 示例4：高级配置 - 包含token统计
        System.out.println("\n=== 高级配置示例 ===");
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(Arrays.asList(
            ChatMessage.system("你是一个Python编程专家"),
            ChatMessage.user("写一个快速排序算法")
        ));
        request.setMaxTokens(500);
        request.setTemperature(0.3);  // 降低随机性，让代码更稳定
        request.setStream(true);      // 启用流式响应
        
        StringBuilder fullResponse = new StringBuilder();
        CountDownLatch latch2 = new CountDownLatch(1);
        
        openai.createChatCompletionStream(request,
            chunk -> {
                String content = chunk.getContent();
                if (content != null) {
                    System.out.print(content);
                    fullResponse.append(content);
                }
                
                // 检查是否包含使用统计
                if (chunk.getUsage() != null) {
                    System.out.println("\n\nToken使用统计:");
                    System.out.println("- 输入tokens: " + chunk.getUsage().getPromptTokens());
                    System.out.println("- 输出tokens: " + chunk.getUsage().getCompletionTokens());
                    System.out.println("- 总计tokens: " + chunk.getUsage().getTotalTokens());
                }
            },
            () -> latch2.countDown(),
            error -> {
                error.printStackTrace();
                latch2.countDown();
            }
        );
        
        latch2.await();
        System.out.println("\n响应长度: " + fullResponse.length() + " 字符");
    }
}
```

#### 在Spring Boot项目中使用（处理凭证冲突）

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import io.github.twwch.openai.sdk.OpenAI;

@Configuration
public class BedrockConfiguration {
    
    // 使用Bedrock API Key，完全隔离其他AWS服务凭证
    @Bean
    public OpenAI bedrockClient(
        @Value("${bedrock.api-key}") String apiKey,
        @Value("${bedrock.session-token}") String sessionToken,
        @Value("${bedrock.region:us-east-2}") String region,
        @Value("${bedrock.model:anthropic.claude-3-5-sonnet-20240620-v1:0}") String model
    ) {
        // 即使项目中配置了其他AWS服务（如S3），
        // Bedrock也会使用这里提供的独立凭证
        return OpenAI.bedrock(region, apiKey, sessionToken, model);
    }
}

// application.yml
/*
bedrock:
  api-key: BedrockAPIKey-c1cg-at-448479377682
  session-token: ABSKQmVkcm9ja0FQSS1jM...
  region: us-east-2
  model: anthropic.claude-3-5-sonnet-20240620-v1:0

# 其他AWS服务配置不会影响Bedrock
aws:
  s3:
    access-key: AKIA...  # S3专用凭证
    secret-key: ...
*/
```

#### 凭证获取方式

1. **标准AWS凭证**：
   - 登录AWS Console → IAM → Users → Security credentials
   - 创建Access Key
   - 需要附加Bedrock相关权限策略

2. **Bedrock API Key**：
   - 专门为Bedrock设计的认证方式
   - 提供更好的隔离性，避免与其他AWS服务冲突
   - 联系AWS获取Bedrock API Key

**重要提示**：
- 始终使用显式凭证，不要依赖环境变量或IAM角色
- Bedrock API Key方式可以完全避免凭证冲突问题
- 详见[AWS凭证隔离指南](AWS_CREDENTIAL_ISOLATION_GUIDE.md)和[Spring Boot集成指南](SPRING_BOOT_INTEGRATION_GUIDE.md)

### Function Calling (Tool Use)

```java
import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.*;
import java.util.*;

public class FunctionCallingExample {
    public static void main(String[] args) {
        OpenAI openai = new OpenAI("your-api-key");
        
        // Define a function
        ChatCompletionRequest.Function weatherFunction = new ChatCompletionRequest.Function();
        weatherFunction.setName("get_weather");
        weatherFunction.setDescription("Get the current weather in a given location");
        
        // Define function parameters
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> locationParam = new HashMap<>();
        locationParam.put("type", "string");
        locationParam.put("description", "The city and state, e.g. San Francisco, CA");
        properties.put("location", locationParam);
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("location"));
        weatherFunction.setParameters(parameters);
        
        // Create chat request with function
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-3.5-turbo");
        request.setMessages(Arrays.asList(
            ChatMessage.user("What's the weather like in Boston?")
        ));
        request.setFunctions(Arrays.asList(weatherFunction));
        
        // Get response
        ChatCompletionResponse response = openai.createChatCompletion(request);
        ChatMessage message = response.getMessage();
        
        // Check if function was called
        if (message.getFunctionCall() != null) {
            String functionName = message.getFunctionCall().getName();
            String arguments = message.getFunctionCall().getArguments();
            System.out.println("Function called: " + functionName);
            System.out.println("Arguments: " + arguments);
            
            // Execute function and send result back
            String weatherResult = getWeather(arguments); // Your function implementation
            
            // Continue conversation with function result
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.user("What's the weather like in Boston?"));
            messages.add(message); // Assistant's message with function call
            messages.add(ChatMessage.function("get_weather", weatherResult));
            
            request.setMessages(messages);
            request.setFunctions(null); // Optional: remove functions for final response
            
            ChatCompletionResponse finalResponse = openai.createChatCompletion(request);
            System.out.println(finalResponse.getContent());
        }
    }
    
    private static String getWeather(String arguments) {
        // Parse arguments and return weather data
        return "{\"temperature\": \"22°C\", \"description\": \"Sunny\"}";
    }
}
```

### Streaming Responses

```java
import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.*;

public class StreamExample {
    public static void main(String[] args) {
        OpenAI openai = new OpenAI("your-api-key");
        
        // Simple streaming
        openai.chatStream("gpt-3.5-turbo", "Tell me a story", 
            content -> System.out.print(content)
        );
        
        // Streaming with system prompt
        openai.chatStream("gpt-3.5-turbo", 
            "You are a helpful assistant", 
            "What is the weather today?",
            content -> System.out.print(content)
        );
        
        // Advanced streaming with full control
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-3.5-turbo");
        request.setMessages(Arrays.asList(
            ChatMessage.user("Write a haiku")
        ));
        
        openai.createChatCompletionStream(
            request,
            chunk -> {
                // Handle each chunk
                String content = chunk.getContent();
                if (content != null) {
                    System.out.print(content);
                }
            },
            () -> System.out.println("\n[Stream completed]"),
            error -> System.err.println("Error: " + error.getMessage())
        );
    }
}
```

### Streaming with Usage Tracking

The SDK automatically includes token usage information in streaming responses:

```java
import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.*;
import java.util.concurrent.CountDownLatch;

public class StreamWithUsageExample {
    public static void main(String[] args) throws InterruptedException {
        OpenAI openai = new OpenAI("your-api-key");
        
        // Create a latch to wait for completion
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder fullResponse = new StringBuilder();
        
        // Create request
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-3.5-turbo");
        request.setMessages(Arrays.asList(
            ChatMessage.system("You are a helpful assistant"),
            ChatMessage.user("Tell me about the benefits of exercise")
        ));
        request.setMaxTokens(150);
        
        System.out.println("AI Response:");
        System.out.println("-".repeat(50));
        
        // Stream with usage tracking
        openai.createChatCompletionStream(
            request,
            chunk -> {
                // Handle content chunks
                String content = chunk.getContent();
                if (content != null) {
                    System.out.print(content);
                    fullResponse.append(content);
                }
                
                // Handle finish reason
                if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                    String finishReason = chunk.getChoices().get(0).getFinishReason();
                    if (finishReason != null) {
                        System.out.println("\n\n[Finish reason: " + finishReason + "]");
                    }
                }
                
                // Handle usage data (automatically included)
                if (chunk.getUsage() != null) {
                    System.out.println("\n" + "-".repeat(50));
                    System.out.println("Token Usage Statistics:");
                    System.out.println("  • Input tokens: " + chunk.getUsage().getPromptTokens());
                    System.out.println("  • Output tokens: " + chunk.getUsage().getCompletionTokens());
                    System.out.println("  • Total tokens: " + chunk.getUsage().getTotalTokens());
                }
            },
            () -> {
                System.out.println("\n[Stream completed successfully]");
                latch.countDown();
            },
            error -> {
                System.err.println("\n[Error: " + error.getMessage() + "]");
                latch.countDown();
            }
        );
        
        // Wait for completion
        latch.await();
        
        System.out.println("\nTotal response length: " + fullResponse.length() + " characters");
    }
}
```

**Note**: The SDK automatically adds `stream_options: { include_usage: true }` to all streaming requests, so you'll receive token usage information at the end of each stream without any additional configuration.

### Using Tools (Recommended for newer models)

```java
import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.*;
import java.util.*;

public class ToolUseExample {
    public static void main(String[] args) {
        OpenAI openai = new OpenAI("your-api-key");
        
        // Define a tool
        ChatCompletionRequest.Tool weatherTool = new ChatCompletionRequest.Tool();
        weatherTool.setType("function");
        
        ChatCompletionRequest.Function function = new ChatCompletionRequest.Function();
        function.setName("get_weather");
        function.setDescription("Get weather information for a location");
        
        // Define parameters (same as function calling example)
        Map<String, Object> parameters = new HashMap<>();
        // ... (same parameter setup as above)
        function.setParameters(parameters);
        weatherTool.setFunction(function);
        
        // Create request with tools
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-4-turbo-preview");
        request.setMessages(Arrays.asList(
            ChatMessage.user("What's the weather in Tokyo and Paris?")
        ));
        request.setTools(Arrays.asList(weatherTool));
        
        // Get response
        ChatCompletionResponse response = openai.createChatCompletion(request);
        ChatMessage message = response.getMessage();
        
        // Check for tool calls
        if (message.getToolCalls() != null) {
            for (ChatMessage.ToolCall toolCall : message.getToolCalls()) {
                String toolName = toolCall.getFunction().getName();
                String arguments = toolCall.getFunction().getArguments();
                String toolCallId = toolCall.getId();
                
                System.out.println("Tool called: " + toolName);
                System.out.println("Arguments: " + arguments);
                
                // Execute tool and prepare response
                String result = executeWeatherTool(arguments);
                
                // Add tool response to conversation
                ChatMessage toolMessage = ChatMessage.tool(toolName, result);
                // Note: In actual implementation, you'd need to set the tool_call_id
            }
        }
    }
    
    private static String executeWeatherTool(String arguments) {
        // Parse JSON arguments and return weather data
        return "{\"temperature\": \"18°C\", \"description\": \"Cloudy\"}";
    }
}
```

## Troubleshooting

### AWS Bedrock Integration Issues

If you encounter credential conflicts or 403 errors when using Bedrock in projects with existing AWS configurations:

1. **Always use explicit credentials** - The SDK enforces credential isolation to prevent conflicts
2. **Check the guides**:
   - [AWS Credential Isolation Guide](AWS_CREDENTIAL_ISOLATION_GUIDE.md) - Detailed explanation of credential isolation
   - [Spring Boot Integration Guide](SPRING_BOOT_INTEGRATION_GUIDE.md) - Special considerations for Spring Boot projects
   - [External Project Integration Issue Guide](EXTERNAL_PROJECT_INTEGRATION_ISSUE.md) - Solutions for dependency conflicts

3. **Run diagnostics** - Use the included `BedrockDependencyDiagnostic` tool to check for dependency conflicts

### Common Issues

- **METRIC_VALUES error**: The SDK automatically filters unsupported OpenAI parameters for Bedrock
- **Missing usage information**: Token usage is automatically included in streaming responses
- **Tool calling with Bedrock**: The SDK handles format conversion between OpenAI and Bedrock APIs

## Project Structure

```
openai-sdk/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── io/github/twwch/openai/sdk/
│   │           ├── OpenAI.java              # Main client class
│   │           ├── OpenAIConfig.java        # Configuration for OpenAI
│   │           ├── AzureOpenAIConfig.java   # Configuration for Azure OpenAI
│   │           ├── BedrockConfig.java       # Configuration for AWS Bedrock
│   │           ├── exception/               # Custom exceptions
│   │           ├── http/                    # HTTP client implementation
│   │           ├── model/                   # Request/Response models
│   │           ├── service/                 # Service implementations
│   │           │   ├── OpenAIService.java   # OpenAI service
│   │           │   ├── BedrockService.java  # Bedrock service
│   │           │   └── bedrock/             # Bedrock model adapters
│   │           └── example/                 # Example usage
│   └── test/
│       └── java/
│           └── io/github/twwch/openai/sdk/
│               ├── OpenAITest.java          # OpenAI tests
│               └── BedrockTest.java         # Bedrock tests
├── pom.xml
├── .gitignore
├── README.md
├── BEDROCK_USAGE.md                        # Detailed Bedrock usage guide
├── AWS_CREDENTIAL_ISOLATION_GUIDE.md       # AWS credential isolation explanation
├── SPRING_BOOT_INTEGRATION_GUIDE.md        # Spring Boot integration guide
└── EXTERNAL_PROJECT_INTEGRATION_ISSUE.md   # External project integration troubleshooting
```

## API Reference

### OpenAI Client Creation

```java
// Basic client
OpenAI openai = new OpenAI("api-key");

// With custom base URL
OpenAI openai = new OpenAI("api-key", "https://api.openai.com/v1");

// Azure OpenAI
OpenAI openai = OpenAI.azure("api-key", "resource-name", "deployment-id");

// AWS Bedrock
OpenAI openai = OpenAI.bedrock("region", "model-id");
OpenAI openai = OpenAI.bedrock("region", "access-key", "secret-key", "model-id");
```

### Chat Completions

```java
// Simple chat
String response = openai.chat("gpt-3.5-turbo", "Your prompt");

// Chat with system prompt
String response = openai.chat("gpt-3.5-turbo", "System prompt", "User prompt");

// Full control with ChatCompletionRequest
ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("gpt-3.5-turbo");
request.setMessages(messages);
request.setTemperature(0.7);
request.setMaxTokens(1000);
// ... set other parameters

ChatCompletionResponse response = openai.createChatCompletion(request);
```

### Function Calling

```java
// Define function
ChatCompletionRequest.Function function = new ChatCompletionRequest.Function();
function.setName("function_name");
function.setDescription("Function description");
function.setParameters(parametersMap);

// Add to request
request.setFunctions(Arrays.asList(function));

// Handle response
if (response.getMessage().getFunctionCall() != null) {
    // Process function call
}
```

### Tool Use

```java
// Define tool
ChatCompletionRequest.Tool tool = new ChatCompletionRequest.Tool();
tool.setType("function");
tool.setFunction(function);

// Add to request
request.setTools(Arrays.asList(tool));

// Handle response
if (response.getMessage().getToolCalls() != null) {
    // Process tool calls
}
```

### Streaming

```java
// Simple streaming
openai.chatStream("gpt-3.5-turbo", "Your prompt", 
    content -> System.out.print(content));

// Advanced streaming
openai.createChatCompletionStream(request,
    chunk -> { /* handle chunk */ },
    () -> { /* on complete */ },
    error -> { /* handle error */ }
);
```

## Building from Source

```bash
# Clone the repository
git clone https://github.com/twwch/openai-sdk.git
cd openai-sdk

# Build with Maven
mvn clean install

# Run tests
mvn test
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Author

- **chenhao** - twwch97780@gmail.com

## Links

- [GitHub Repository](https://github.com/twwch/openai-sdk)
- [OpenAI API Documentation](https://platform.openai.com/docs/api-reference)
- [Azure OpenAI Documentation](https://learn.microsoft.com/en-us/azure/ai-services/openai/)
- [AWS Bedrock Documentation](https://docs.aws.amazon.com/bedrock/)
- [Detailed Bedrock Usage Guide](BEDROCK_USAGE.md)