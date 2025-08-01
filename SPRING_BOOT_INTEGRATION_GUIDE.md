# Spring Boot项目集成指南

## 场景说明

当您的Spring Boot项目已经配置了AWS服务（如S3），但这些凭证没有Bedrock权限时，集成openai-sdk可能会遇到凭证冲突问题。本指南提供完整的解决方案。

## 问题症状

```
Bedrock流式请求失败: 403
The security token included in the request is invalid
```

即使您显式提供了Bedrock凭证，仍然可能出现上述错误。

## 解决方案

### 方案1：使用独立的配置类（推荐）

创建专门的Bedrock配置类，确保凭证隔离：

```java
@Configuration
public class BedrockConfiguration {
    
    @Value("${bedrock.access-key-id}")
    private String bedrockAccessKey;
    
    @Value("${bedrock.secret-access-key}")
    private String bedrockSecretKey;
    
    @Value("${bedrock.session-token:}")
    private String bedrockSessionToken;
    
    @Value("${bedrock.region:us-east-2}")
    private String bedrockRegion;
    
    @Value("${bedrock.model-id:anthropic.claude-3-5-sonnet-20240620-v1:0}")
    private String modelId;
    
    @Bean
    @Primary
    public OpenAI bedrockClient() {
        // 使用专门的Bedrock凭证
        if (StringUtils.hasText(bedrockSessionToken)) {
            return OpenAI.bedrock(bedrockRegion, bedrockAccessKey, 
                bedrockSecretKey, bedrockSessionToken, modelId);
        } else {
            return OpenAI.bedrock(bedrockRegion, bedrockAccessKey, 
                bedrockSecretKey, modelId);
        }
    }
    
    @Bean
    public BedrockService bedrockService(OpenAI bedrockClient) {
        return new BedrockService(bedrockClient);
    }
}
```

在`application.yml`中配置：

```yaml
# S3配置（已存在）
aws:
  region: us-west-2
  accessKeyId: ${AWS_ACCESS_KEY_ID_S3}
  secretAccessKey: ${AWS_SECRET_ACCESS_KEY_S3}

# Bedrock配置（新增）
bedrock:
  region: us-east-2
  access-key-id: ${BEDROCK_ACCESS_KEY_ID}
  secret-access-key: ${BEDROCK_SECRET_ACCESS_KEY}
  session-token: ${BEDROCK_SESSION_TOKEN:}
  model-id: anthropic.claude-3-5-sonnet-20240620-v1:0
```

### 方案2：使用Profile隔离

配置多个AWS Profile：

```java
@Configuration
public class AwsConfiguration {
    
    @Bean
    @Primary
    public S3Client s3Client() {
        return S3Client.builder()
            .credentialsProvider(ProfileCredentialsProvider.create("s3-profile"))
            .region(Region.US_WEST_2)
            .build();
    }
    
    @Bean
    public OpenAI bedrockClient() {
        // Bedrock使用显式凭证，不受Profile影响
        return OpenAI.bedrock(
            "us-east-2",
            "BedrockAPIKey-xxx",
            "xxx",
            "anthropic.claude-3-5-sonnet-20240620-v1:0"
        );
    }
}
```

### 方案3：使用条件配置

根据环境动态选择凭证：

```java
@Configuration
@ConditionalOnProperty(name = "bedrock.enabled", havingValue = "true")
public class BedrockAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public OpenAI bedrockClient(BedrockProperties properties) {
        return OpenAI.bedrock(
            properties.getRegion(),
            properties.getAccessKeyId(),
            properties.getSecretAccessKey(),
            properties.getModelId()
        );
    }
}

@Component
@ConfigurationProperties(prefix = "bedrock")
@Data
public class BedrockProperties {
    private boolean enabled = true;
    private String region = "us-east-2";
    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;
    private String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";
}
```

## 使用示例

### 1. 服务层使用

```java
@Service
public class ChatService {
    
    private final OpenAI bedrockClient;
    
    @Autowired
    public ChatService(OpenAI bedrockClient) {
        this.bedrockClient = bedrockClient;
    }
    
    public String chat(String message) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(Arrays.asList(
            ChatMessage.user(message)
        ));
        request.setMaxTokens(100);
        
        try {
            ChatCompletionResponse response = bedrockClient.createChatCompletion(request);
            return response.getChoices().get(0).getMessage().getContent();
        } catch (Exception e) {
            log.error("Bedrock请求失败", e);
            throw new RuntimeException("聊天服务暂时不可用");
        }
    }
}
```

### 2. 控制器使用

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private final ChatService chatService;
    
    @Autowired
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }
    
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            String response = chatService.chat(request.getMessage());
            return ResponseEntity.ok(new ChatResponse(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ChatResponse("服务暂时不可用"));
        }
    }
}

@Data
class ChatRequest {
    private String message;
}

@Data
@AllArgsConstructor
class ChatResponse {
    private String response;
}
```

## 环境变量配置

### 开发环境 (.env.local)

```bash
# S3凭证
AWS_ACCESS_KEY_ID_S3=AKIA5FCKX2MX4E67ZVF6
AWS_SECRET_ACCESS_KEY_S3=xxx

# Bedrock凭证
BEDROCK_ACCESS_KEY_ID=BedrockAPIKey-xxx
BEDROCK_SECRET_ACCESS_KEY=xxx
```

### 生产环境

使用环境变量或密钥管理服务：

```bash
# 使用AWS Systems Manager Parameter Store
aws ssm put-parameter \
    --name "/myapp/bedrock/access-key-id" \
    --value "BedrockAPIKey-xxx" \
    --type "SecureString"

aws ssm put-parameter \
    --name "/myapp/bedrock/secret-access-key" \
    --value "xxx" \
    --type "SecureString"
```

在Spring Boot中使用：

```java
@Configuration
public class ParameterStoreConfiguration {
    
    @Bean
    public OpenAI bedrockClient(SsmClient ssmClient) {
        String accessKey = getParameter(ssmClient, "/myapp/bedrock/access-key-id");
        String secretKey = getParameter(ssmClient, "/myapp/bedrock/secret-access-key");
        
        return OpenAI.bedrock("us-east-2", accessKey, secretKey, 
            "anthropic.claude-3-5-sonnet-20240620-v1:0");
    }
    
    private String getParameter(SsmClient ssmClient, String name) {
        GetParameterRequest request = GetParameterRequest.builder()
            .name(name)
            .withDecryption(true)
            .build();
        return ssmClient.getParameter(request).parameter().value();
    }
}
```

## 测试配置

### 单元测试

```java
@SpringBootTest
@TestPropertySource(properties = {
    "bedrock.access-key-id=test-key",
    "bedrock.secret-access-key=test-secret",
    "bedrock.region=us-east-2"
})
public class ChatServiceTest {
    
    @MockBean
    private OpenAI bedrockClient;
    
    @Autowired
    private ChatService chatService;
    
    @Test
    public void testChat() throws Exception {
        // Mock响应
        ChatCompletionResponse mockResponse = new ChatCompletionResponse();
        when(bedrockClient.createChatCompletion(any()))
            .thenReturn(mockResponse);
        
        // 测试
        String response = chatService.chat("Hello");
        assertNotNull(response);
    }
}
```

### 集成测试

```java
@SpringBootTest
@ActiveProfiles("integration-test")
public class BedrockIntegrationTest {
    
    @Autowired
    private OpenAI bedrockClient;
    
    @Test
    @Disabled("需要真实的Bedrock凭证")
    public void testRealBedrockCall() throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(Arrays.asList(
            ChatMessage.user("回复：OK")
        ));
        request.setMaxTokens(10);
        
        ChatCompletionResponse response = bedrockClient.createChatCompletion(request);
        assertNotNull(response);
        assertFalse(response.getChoices().isEmpty());
    }
}
```

## 故障排除

### 1. 检查凭证来源

添加调试日志：

```java
@Component
public class BedrockDebugger {
    
    @PostConstruct
    public void debugCredentials() {
        if (log.isDebugEnabled()) {
            log.debug("AWS环境变量: {}", System.getenv("AWS_ACCESS_KEY_ID"));
            log.debug("AWS系统属性: {}", System.getProperty("aws.accessKeyId"));
            log.debug("Bedrock配置: 已加载");
        }
    }
}
```

### 2. 验证凭证隔离

```java
@Component
public class BedrockHealthCheck implements HealthIndicator {
    
    private final OpenAI bedrockClient;
    
    @Autowired
    public BedrockHealthCheck(OpenAI bedrockClient) {
        this.bedrockClient = bedrockClient;
    }
    
    @Override
    public Health health() {
        try {
            // 简单的健康检查
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setMessages(Arrays.asList(ChatMessage.user("ping")));
            request.setMaxTokens(5);
            
            bedrockClient.createChatCompletion(request);
            return Health.up()
                .withDetail("service", "bedrock")
                .withDetail("status", "connected")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("service", "bedrock")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### 3. 日志配置

```yaml
logging:
  level:
    io.github.twwch.openai.sdk: DEBUG
    software.amazon.awssdk: INFO
    software.amazon.awssdk.auth: DEBUG
```

## 最佳实践

1. **凭证管理**
   - 永远不要硬编码凭证
   - 使用环境变量或密钥管理服务
   - 为不同环境使用不同的凭证

2. **错误处理**
   - 实现重试机制
   - 提供降级方案
   - 记录详细的错误日志

3. **性能优化**
   - 使用连接池
   - 实现缓存机制
   - 监控API使用量

4. **安全考虑**
   - 限制API访问权限
   - 实现请求签名验证
   - 监控异常使用模式

## 示例项目结构

```
src/
├── main/
│   ├── java/
│   │   └── com/example/myapp/
│   │       ├── config/
│   │       │   ├── BedrockConfiguration.java
│   │       │   └── AwsConfiguration.java
│   │       ├── service/
│   │       │   └── ChatService.java
│   │       ├── controller/
│   │       │   └── ChatController.java
│   │       └── Application.java
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       └── application-prod.yml
└── test/
    └── java/
        └── com/example/myapp/
            └── service/
                └── ChatServiceTest.java
```

## 总结

通过正确的配置和凭证隔离，您可以在Spring Boot项目中同时使用多个AWS服务而不会发生凭证冲突。关键是：

1. 使用独立的配置管理Bedrock凭证
2. 避免依赖默认的AWS凭证链
3. 实施适当的错误处理和监控
4. 遵循安全最佳实践