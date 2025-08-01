# AWS凭证隔离指南

## 问题描述

当项目中已经配置了AWS凭证（例如用于S3），但这些凭证没有Bedrock权限时，即使显式传入Bedrock专用凭证，仍然会出现403错误。

## 问题原因

AWS SDK默认会从多个来源查找凭证（按优先级）：
1. 环境变量 (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
2. 系统属性 (aws.accessKeyId, aws.secretAccessKey)
3. AWS凭证文件 (~/.aws/credentials)
4. ECS容器凭证
5. EC2实例元数据

在Spring Boot项目中，通常会通过配置文件设置AWS凭证：
```yaml
aws:
  region: us-west-2
  accessKeyId: AKIA5FCKX2MX4E67ZVF6  # S3凭证，无Bedrock权限
  secretAccessKey: kHwWFpXi1yoXh7BDCkKDSeVNbUveB/h4PP3
```

这些凭证会被Spring自动配置到环境中，导致Bedrock SDK使用错误的凭证。

## 解决方案

### 已实施的修改

1. **强制使用显式凭证**
   - 移除了默认凭证链的使用
   - 必须提供accessKeyId和secretAccessKey
   - 使用StaticCredentialsProvider确保凭证隔离

2. **通过显式提供凭证来避免默认凭证链**
   当使用StaticCredentialsProvider时，AWS SDK不会使用默认凭证链，因此不需要额外的配置来禁用它。

### 使用方法

```java
// 正确的使用方式 - 显式提供Bedrock凭证
String bearerKey = "BedrockAPIKey-c1cg-at-448479377682";
String bearerToken = "ABSKQmVkcm9ja0FQSU...";
String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";

OpenAI client = OpenAI.bedrock("us-east-2", bearerKey, bearerToken, modelId);
```

### Spring Boot项目特殊配置

如果仍然遇到凭证冲突，可以在Spring Boot项目中添加以下配置：

1. **创建独立的Bedrock配置类**
```java
@Configuration
public class BedrockConfig {
    
    @Bean
    public OpenAI bedrockClient() {
        // 使用专门的Bedrock凭证
        String bearerKey = "BedrockAPIKey-xxx";
        String bearerToken = "xxx";
        String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";
        
        return OpenAI.bedrock("us-east-2", bearerKey, bearerToken, modelId);
    }
}
```

2. **环境隔离方案**
```java
// 临时清除AWS环境变量（仅用于测试）
@TestConfiguration
public class BedrockTestConfig {
    
    @PostConstruct
    public void clearAwsEnv() {
        // 备份原始值
        String originalKey = System.getenv("AWS_ACCESS_KEY_ID");
        String originalSecret = System.getenv("AWS_SECRET_ACCESS_KEY");
        
        // 清除环境变量
        Map<String, String> env = new HashMap<>(System.getenv());
        env.remove("AWS_ACCESS_KEY_ID");
        env.remove("AWS_SECRET_ACCESS_KEY");
        
        // 注意：实际项目中需要更安全的方式处理
    }
}
```

### 调试方法

1. **检查实际使用的凭证**
```java
// 在BedrockService中添加调试日志
System.out.println("使用的AccessKeyId前缀: " + 
    config.getAccessKeyId().substring(0, 10) + "...");
```

2. **验证凭证来源**
```java
// 检查环境变量
System.out.println("环境变量 AWS_ACCESS_KEY_ID: " + 
    System.getenv("AWS_ACCESS_KEY_ID"));
System.out.println("系统属性 aws.accessKeyId: " + 
    System.getProperty("aws.accessKeyId"));
```

### 最佳实践

1. **使用环境变量分离**
   ```bash
   # S3凭证
   export AWS_ACCESS_KEY_ID_S3=AKIA5FCKX2MX4E67ZVF6
   export AWS_SECRET_ACCESS_KEY_S3=xxx
   
   # Bedrock凭证
   export AWS_ACCESS_KEY_ID_BEDROCK=BedrockAPIKey-xxx
   export AWS_SECRET_ACCESS_KEY_BEDROCK=xxx
   ```

2. **使用配置文件分离**
   ```yaml
   # application.yml
   aws:
     s3:
       accessKeyId: ${AWS_ACCESS_KEY_ID_S3}
       secretAccessKey: ${AWS_SECRET_ACCESS_KEY_S3}
     bedrock:
       accessKeyId: ${AWS_ACCESS_KEY_ID_BEDROCK}
       secretAccessKey: ${AWS_SECRET_ACCESS_KEY_BEDROCK}
   ```

3. **使用Profile分离**
   ```ini
   # ~/.aws/credentials
   [default]
   aws_access_key_id = AKIA5FCKX2MX4E67ZVF6
   aws_secret_access_key = xxx
   
   [bedrock]
   aws_access_key_id = BedrockAPIKey-xxx
   aws_secret_access_key = xxx
   ```

### 故障排除

如果仍然遇到403错误：

1. **确认凭证格式**
   - BedrockAPIKey格式：`BedrockAPIKey-xxx`
   - SessionToken格式：Base64编码的token

2. **检查区域**
   - 确保使用正确的区域（如us-east-2）
   - Claude 3.7可能只在特定区域可用

3. **验证权限**
   ```bash
   # 使用AWS CLI测试
   aws bedrock-runtime invoke-model \
     --model-id anthropic.claude-3-sonnet-20240229 \
     --region us-east-2 \
     --body '{"messages":[{"role":"user","content":"test"}],"anthropic_version":"bedrock-2023-05-31","max_tokens":10}'
   ```

4. **检查模型ID**
   - 使用稳定的模型ID，如`anthropic.claude-3-5-sonnet-20240620-v1:0`
   - 避免使用可能有问题的新模型

### 示例：完整的隔离方案

```java
@Component
public class BedrockClientFactory {
    
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
        // 确保使用专门的Bedrock凭证
        if (StringUtils.hasText(bedrockSessionToken)) {
            return OpenAI.bedrock(bedrockRegion, bedrockAccessKey, 
                bedrockSecretKey, bedrockSessionToken, modelId);
        } else {
            return OpenAI.bedrock(bedrockRegion, bedrockAccessKey, 
                bedrockSecretKey, modelId);
        }
    }
}
```

## 总结

通过以上修改，OpenAI SDK的Bedrock服务现在会：
1. 强制要求显式提供凭证
2. 不会从环境变量或其他来源获取凭证
3. 完全隔离Bedrock凭证，避免与其他AWS服务凭证冲突

这确保了即使在已配置其他AWS服务的项目中，Bedrock也能使用正确的凭证。