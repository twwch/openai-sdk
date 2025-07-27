# OpenAI Java SDK

A Java SDK for interacting with OpenAI and Azure OpenAI APIs.

## Overview

This SDK provides a simple and intuitive way to integrate OpenAI services into your Java applications. It supports both standard OpenAI API and Azure OpenAI Service endpoints.

## Features

- Easy-to-use API client for OpenAI services
- Support for both OpenAI and Azure OpenAI endpoints
- Chat completion API support
- Configurable HTTP client
- Comprehensive error handling
- Type-safe request/response models

## Requirements

- Java 11 or higher
- Maven 3.6 or higher

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.twwch</groupId>
    <artifactId>openai-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

### OpenAI Example

```java
import com.openai.sdk.OpenAI;
import com.openai.sdk.OpenAIConfig;
import com.openai.sdk.model.chat.*;

public class Example {
    public static void main(String[] args) {
        // Configure OpenAI client
        OpenAIConfig config = new OpenAIConfig.Builder()
            .apiKey("your-api-key")
            .build();
        
        OpenAI openai = new OpenAI(config);
        
        // Create a chat completion request
        ChatCompletionRequest request = new ChatCompletionRequest.Builder()
            .model("gpt-3.5-turbo")
            .messages(List.of(
                new ChatMessage("system", "You are a helpful assistant."),
                new ChatMessage("user", "Hello!")
            ))
            .build();
        
        // Get response
        ChatCompletionResponse response = openai.chatCompletions().create(request);
        System.out.println(response.getChoices().get(0).getMessage().getContent());
    }
}
```

### Azure OpenAI Example

```java
import com.openai.sdk.OpenAI;
import com.openai.sdk.AzureOpenAIConfig;
import com.openai.sdk.model.chat.*;

public class AzureExample {
    public static void main(String[] args) {
        // Configure Azure OpenAI client
        AzureOpenAIConfig config = new AzureOpenAIConfig.Builder()
            .apiKey("your-azure-api-key")
            .endpoint("https://your-resource.openai.azure.com/")
            .deploymentId("your-deployment-id")
            .apiVersion("2023-12-01-preview")
            .build();
        
        OpenAI openai = new OpenAI(config);
        
        // Usage is the same as standard OpenAI
        ChatCompletionRequest request = new ChatCompletionRequest.Builder()
            .messages(List.of(
                new ChatMessage("user", "Tell me a joke")
            ))
            .build();
        
        ChatCompletionResponse response = openai.chatCompletions().create(request);
        System.out.println(response.getChoices().get(0).getMessage().getContent());
    }
}
```

## Project Structure

```
openai-sdk/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/openai/sdk/
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
│           └── com/openai/sdk/
│               └── OpenAITest.java          # Unit tests
├── pom.xml
├── .gitignore
└── README.md
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