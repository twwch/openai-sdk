# OpenAI Java SDK

A Java SDK for interacting with OpenAI and Azure OpenAI APIs.

## Overview

This SDK provides a simple and intuitive way to integrate OpenAI services into your Java applications. It supports both standard OpenAI API and Azure OpenAI Service endpoints.

## Features

- Easy-to-use API client for OpenAI services
- Support for both OpenAI and Azure OpenAI endpoints
- Chat completion API support
- Streaming responses support
- Function calling and tool use support
- Configurable HTTP client
- Comprehensive error handling
- Type-safe request/response models
- Simplified API methods for common use cases

## Requirements

- Java 11 or higher
- Maven 3.6 or higher

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.twwch</groupId>
    <artifactId>openai-sdk</artifactId>
    <version>1.1.1</version>
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
│   │           ├── exception/               # Custom exceptions
│   │           ├── http/                    # HTTP client implementation
│   │           ├── model/                   # Request/Response models
│   │           ├── service/                 # Service implementations
│   │           └── example/                 # Example usage
│   └── test/
│       └── java/
│           └── io/github/twwch/openai/sdk/
│               └── OpenAITest.java          # Unit tests
├── pom.xml
├── .gitignore
└── README.md
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