# Bedrock凭证隔离方案V2

## 背景

在集成Bedrock SDK到已有AWS服务的项目时，经常遇到凭证冲突问题：
- 项目已配置AWS凭证（如S3），但这些凭证没有Bedrock权限
- 即使显式传入Bedrock专用凭证，AWS SDK仍可能使用环境中的其他凭证
- 导致403权限错误

## 解决方案架构

### 1. BedrockApiKeyCredentialsProvider
专用的凭证提供者，完全绕过AWS SDK的默认凭证链：

```java
public class BedrockApiKeyCredentialsProvider implements AwsCredentialsProvider {
    // 强制返回提供的凭证，不进行任何链式查找
    @Override
    public AwsCredentials resolveCredentials() {
        if (sessionToken != null) {
            return AwsSessionCredentials.create(apiKey, apiSecret, sessionToken);
        } else {
            return AwsBasicCredentials.create(apiKey, apiSecret);
        }
    }
}
```

### 2. BedrockCredentialsIsolator
凭证隔离器，在创建客户端时临时清除环境凭证：

```java
public class BedrockCredentialsIsolator {
    public static BedrockRuntimeClient createIsolatedClient(...) {
        // 1. 创建隔离的凭证提供者
        AwsCredentialsProvider provider = new BedrockApiKeyCredentialsProvider(...);
        
        // 2. 临时清除系统属性中的AWS凭证
        Map<String, String> backup = clearAwsEnvironment();
        
        try {
            // 3. 创建客户端（此时环境中没有其他AWS凭证）
            return BedrockRuntimeClient.builder()
                .credentialsProvider(provider)
                .build();
        } finally {
            // 4. 恢复原始环境
            restoreEnvironment(backup);
        }
    }
}
```

## 使用方式

### 基本使用
```java
// 使用Bedrock API Key
OpenAI client = OpenAI.bedrock(
    "us-east-2",
    "BedrockAPIKey-xxx",
    "your-session-token",
    "anthropic.claude-3-5-sonnet-20240620-v1:0"
);

// 使用标准AWS凭证
OpenAI client = OpenAI.bedrock(
    "us-east-2",
    "AKIAXXXXXXXX",
    "your-secret-key",
    "anthropic.claude-3-sonnet-20240229"
);
```

### Spring Boot集成
```java
@Configuration
public class BedrockConfiguration {
    
    @Bean
    public OpenAI bedrockClient(
        @Value("${bedrock.region}") String region,
        @Value("${bedrock.access-key}") String accessKey,
        @Value("${bedrock.secret-key}") String secretKey,
        @Value("${bedrock.model}") String model) {
        
        // 隔离的Bedrock客户端，不受其他AWS配置影响
        return OpenAI.bedrock(region, accessKey, secretKey, model);
    }
}
```

## 技术细节

### 凭证隔离流程
1. **验证凭证** - 确保提供了必需的accessKey和secretKey
2. **创建隔离提供者** - 使用BedrockApiKeyCredentialsProvider
3. **清理环境** - 临时移除系统属性中的AWS凭证
4. **创建客户端** - 在干净的环境中创建客户端
5. **恢复环境** - 恢复原始的系统属性

### 隔离的系统属性
```java
String[] awsSystemProps = {
    "aws.accessKeyId",
    "aws.secretAccessKey", 
    "aws.sessionToken",
    "aws.region"
};
```

### 为什么不隔离环境变量？
- Java不允许在运行时修改真实的环境变量
- System.getenv()返回的是不可变Map
- 但系统属性（System.getProperty）可以修改

## 测试验证

### 单元测试
```java
@Test
public void testCredentialIsolation() {
    // 设置冲突的系统属性
    System.setProperty("aws.accessKeyId", "S3_KEY");
    System.setProperty("aws.secretAccessKey", "S3_SECRET");
    
    // 创建Bedrock客户端
    OpenAI client = OpenAI.bedrock(
        "us-east-2",
        "BedrockAPIKey-xxx",
        "bedrock-secret",
        "claude-3"
    );
    
    // 应该使用Bedrock凭证，而不是系统属性中的S3凭证
    ChatCompletionResponse response = client.createChatCompletion(...);
    assertNotNull(response);
}
```

### 集成测试
```java
@SpringBootTest
public class BedrockIntegrationTest {
    
    @Autowired
    private OpenAI bedrockClient;
    
    @Test
    public void testWithExistingAwsConfig() {
        // 即使Spring已配置其他AWS服务
        // Bedrock仍应使用自己的凭证
        String response = bedrockClient.chat("claude", "Hello");
        assertNotNull(response);
    }
}
```

## 常见问题

### Q: 为什么需要这么复杂的隔离机制？
A: AWS SDK设计为自动查找凭证，这在单一服务场景下很方便，但在多服务场景下会造成冲突。

### Q: 这会影响其他AWS服务吗？
A: 不会。凭证清理只在创建Bedrock客户端的瞬间进行，立即恢复。

### Q: 支持哪些凭证格式？
A: 
- Bedrock API Key: `BedrockAPIKey-xxx` + session token
- 标准AWS凭证: `AKIA...` + secret key
- 临时凭证: access key + secret key + session token

### Q: 如何调试凭证问题？
A: 启用调试日志：
```java
System.setProperty("aws.java.v2.log.level", "DEBUG");
```

## 最佳实践

1. **始终使用显式凭证** - 不依赖环境变量或默认凭证链
2. **使用专用配置** - 为Bedrock使用独立的配置项
3. **密钥管理** - 使用密钥管理服务存储凭证
4. **监控和日志** - 记录凭证使用情况（注意不要记录密钥本身）

## 迁移指南

从旧版本迁移：
```java
// 旧版本（可能有凭证冲突）
OpenAI client = OpenAI.bedrock("us-east-2", "claude-3");

// 新版本（强制隔离）
OpenAI client = OpenAI.bedrock(
    "us-east-2",
    "your-access-key",
    "your-secret-key",
    "claude-3"
);
```

## 总结

这个方案通过以下机制确保凭证隔离：
1. 专用的凭证提供者，不使用默认链
2. 创建客户端时临时清理环境
3. 立即恢复环境，不影响其他服务
4. 完全隔离Bedrock凭证与其他AWS服务凭证

这样即使在复杂的多AWS服务环境中，Bedrock也能正确使用指定的凭证。