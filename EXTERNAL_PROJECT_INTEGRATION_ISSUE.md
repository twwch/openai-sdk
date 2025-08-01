# 外部项目集成问题解决方案

## 问题描述
在openai-sdk项目中测试正常，但在其他项目引入后报错：
```
Bedrock流式请求失败: METRIC_VALUES
```

## 可能原因

### 1. 依赖版本冲突
最常见的原因是AWS SDK版本冲突。外部项目可能有不同版本的AWS SDK依赖。

### 2. Jackson版本不兼容
openai-sdk使用Jackson 2.17.2，如果外部项目使用不同版本，可能导致序列化问题。

### 3. 类加载器问题
在某些应用服务器或框架中，类加载顺序可能导致问题。

## 解决方案

### 方案1：使用AWS SDK BOM管理版本

在外部项目的pom.xml中添加：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>2.32.13</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.twwch</groupId>
        <artifactId>openai-sdk</artifactId>
        <version>1.1.11</version>
    </dependency>
</dependencies>
```

### 方案2：排除并重新声明AWS依赖

```xml
<dependency>
    <groupId>io.github.twwch</groupId>
    <artifactId>openai-sdk</artifactId>
    <version>1.1.11</version>
    <exclusions>
        <exclusion>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bedrockruntime</artifactId>
        </exclusion>
        <exclusion>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bedrock</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- 重新声明与项目兼容的版本 -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>bedrockruntime</artifactId>
    <version>2.32.13</version>
</dependency>
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>bedrock</artifactId>
    <version>2.32.13</version>
</dependency>
```

### 方案3：检查和统一Jackson版本

```xml
<properties>
    <jackson.version>2.17.2</jackson.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson</groupId>
            <artifactId>jackson-bom</artifactId>
            <version>${jackson.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 方案4：Spring Boot项目特殊处理

如果外部项目是Spring Boot项目，可能需要：

```properties
# application.properties
spring.jackson.deserialization.fail-on-unknown-properties=false
```

或在配置类中：

```java
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
```

## 诊断步骤

### 1. 运行依赖诊断
在外部项目中运行`BedrockDependencyDiagnostic`类。

### 2. 查看依赖树
```bash
mvn dependency:tree | grep -E "(aws|jackson)"
```

### 3. 检查冲突
```bash
mvn dependency:analyze
```

### 4. 强制使用特定版本
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>core</artifactId>
            <version>2.32.13</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 临时解决方案

如果上述方案都不奏效，可以尝试：

1. **降级到稳定模型**
   ```java
   String modelId = "anthropic.claude-3-sonnet-20240229";
   ```

2. **关闭流式响应**
   ```java
   request.setStream(false);
   ```

3. **创建隔离的ClassLoader**
   ```java
   // 使用独立的类加载器加载openai-sdk
   URLClassLoader isolatedClassLoader = new URLClassLoader(
       new URL[]{openaiSdkJarUrl},
       ClassLoader.getSystemClassLoader().getParent()
   );
   ```

## 推荐的Maven配置

```xml
<!-- 完整的推荐配置 -->
<properties>
    <aws.sdk.version>2.32.13</aws.sdk.version>
    <jackson.version>2.17.2</jackson.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- AWS SDK BOM -->
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>${aws.sdk.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        
        <!-- Jackson BOM -->
        <dependency>
            <groupId>com.fasterxml.jackson</groupId>
            <artifactId>jackson-bom</artifactId>
            <version>${jackson.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.twwch</groupId>
        <artifactId>openai-sdk</artifactId>
        <version>1.1.11</version>
    </dependency>
</dependencies>
```

## 验证修复

修复后，运行以下测试代码验证：

```java
// 简单测试
ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("anthropic.claude-3-sonnet-20240229");
request.setMessages(Arrays.asList(ChatMessage.user("Hello")));
request.setStream(true);

OpenAI client = OpenAI.bedrock("us-east-2", bearerKey, bearerToken, modelId);
client.createChatCompletion(request);
```