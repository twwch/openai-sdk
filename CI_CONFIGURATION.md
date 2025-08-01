# CI配置说明

## 概述

本项目使用GitHub Actions进行持续集成，配置文件位于 `.github/workflows/ci.yml`。

## 测试策略

### 为什么在CI中跳过测试？

1. **外部依赖**：许多测试需要真实的API密钥（OpenAI、Azure、AWS Bedrock）
2. **成本考虑**：运行测试会消耗API配额和产生费用
3. **安全性**：避免在CI环境中暴露敏感的API密钥
4. **环境差异**：某些测试需要特定的AWS配置或网络环境

### CI构建流程

1. **编译检查**：确保代码能够正确编译
2. **打包验证**：确保JAR文件能够正确生成
3. **多版本兼容**：在Java 11和17上进行构建测试

### 本地运行测试

开发者应在本地环境运行测试：

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=OpenAITest

# 跳过某些测试
mvn test -Dtest=!BedrockTest,!BedrockCredentialIsolationTest
```

### 配置测试环境

1. **创建测试配置文件**

创建 `src/test/resources/test.properties`：

```properties
# OpenAI配置
openai.api.key=your-test-api-key

# Azure配置
azure.api.key=your-azure-key
azure.resource.name=your-resource
azure.deployment.id=your-deployment

# Bedrock配置
bedrock.access.key.id=your-bedrock-key
bedrock.secret.access.key=your-bedrock-secret
bedrock.region=us-east-2
```

2. **使用环境变量**

```bash
export OPENAI_API_KEY=your-key
export BEDROCK_ACCESS_KEY_ID=your-key
export BEDROCK_SECRET_ACCESS_KEY=your-secret
```

### Maven配置

在 `pom.xml` 中配置测试：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.0.0-M7</version>
            <configuration>
                <!-- 在CI中跳过测试 -->
                <skipTests>${skipTests}</skipTests>
                <!-- 排除需要真实API的测试 -->
                <excludes>
                    <exclude>**/*IntegrationTest.java</exclude>
                    <exclude>**/Bedrock*Test.java</exclude>
                </excludes>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### 使用Profile

可以使用Maven Profile来管理不同环境：

```xml
<profiles>
    <!-- CI Profile -->
    <profile>
        <id>ci</id>
        <properties>
            <skipTests>true</skipTests>
        </properties>
    </profile>
    
    <!-- 本地开发Profile -->
    <profile>
        <id>dev</id>
        <properties>
            <skipTests>false</skipTests>
        </properties>
    </profile>
</profiles>
```

使用方式：

```bash
# CI构建
mvn clean package -Pci

# 本地开发
mvn clean test -Pdev
```

### 命令行选项

```bash
# 跳过测试编译和执行
mvn clean package -Dmaven.test.skip=true

# 只跳过测试执行（仍编译测试代码）
mvn clean package -DskipTests

# 运行特定测试
mvn test -Dtest=OpenAITest#testChat

# 设置测试超时
mvn test -Dsurefire.timeout=60
```

### 未来改进

1. **Mock测试**：为API调用创建Mock测试，不需要真实凭证
2. **集成测试分离**：将需要真实API的测试分离到独立的集成测试模块
3. **测试容器**：使用Testcontainers或类似工具模拟服务
4. **契约测试**：使用契约测试验证API接口兼容性

### 故障排除

如果在CI中遇到构建问题：

1. 检查Java版本兼容性
2. 清理Maven缓存：`rm -rf ~/.m2/repository`
3. 检查依赖版本冲突：`mvn dependency:tree`
4. 查看详细日志：`mvn -X clean package`

### 贡献指南

提交PR时请注意：

1. 确保代码能够编译通过
2. 在本地运行相关测试
3. 更新相关文档
4. 不要提交包含API密钥的文件