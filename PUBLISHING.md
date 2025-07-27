# 发布到 Maven Central 的新流程指南

## 重要变更
- **JIRA 注册方式已废弃**：自 2024 年 3 月 12 日起，不再使用 JIRA 票证注册
- **OSSRH 将于 2025 年 6 月 30 日停用**：所有新项目必须使用 Central Portal
- **新网址**：https://central.sonatype.com

## 步骤 1：注册 Central Portal 账号

1. 访问 https://central.sonatype.com
2. 点击右上角 "Sign In"
3. 选择注册方式：
   - 使用 Google 或 GitHub 账号
   - 创建用户名和密码

## 步骤 2：创建命名空间

1. 登录后，创建您的命名空间（如 `io.github.twwch`）
2. 等待命名空间验证通过

## 步骤 3：生成访问令牌

1. 在 Central Portal 中生成用户令牌
2. 记录下 username 和 password（这是令牌，不是您的登录密码）

## 步骤 4：配置 Maven settings.xml

在您的 Maven settings.xml 中添加：

```xml
<servers>
    <server>
        <id>central</id>
        <username>您的令牌用户名</username>
        <password>您的令牌密码</password>
    </server>
</servers>
```

## 步骤 5：配置 GPG 签名

1. 安装 GPG：
   ```bash
   # macOS
   brew install gnupg
   
   # 或使用 MacGPG: https://gpgtools.org
   ```

2. 生成密钥：
   ```bash
   gpg --gen-key
   ```

3. 列出密钥：
   ```bash
   gpg --list-secret-keys --keyid-format LONG
   ```

4. 发布公钥到服务器：
   ```bash
   gpg --keyserver keyserver.ubuntu.com --send-keys 您的密钥ID
   ```

## 步骤 6：发布到 Maven Central

### 基本发布（需要手动在 Portal 批准）：
```bash
mvn clean deploy
```

### 自动发布（推荐用于 CI/CD）：
在 pom.xml 中添加配置：
```xml
<plugin>
    <groupId>org.sonatype.central</groupId>
    <artifactId>central-publishing-maven-plugin</artifactId>
    <version>0.8.0</version>
    <extensions>true</extensions>
    <configuration>
        <publishingServerId>central</publishingServerId>
        <autoPublish>true</autoPublish>
        <waitUntil>published</waitUntil>
    </configuration>
</plugin>
```

### 带 GPG 签名发布：
```bash
mvn clean deploy -Dgpg.passphrase="您的GPG密码"
```

## 故障排除

### 错误：Cannot invoke "org.apache.maven.settings.Server.clone()"
- 原因：settings.xml 中缺少 `central` 服务器配置
- 解决：添加上述第 4 步的配置

### 错误：GPG signing failed
- 原因：GPG 未配置或密钥未找到
- 解决：
  ```bash
  # 检查 GPG 是否安装
  gpg --version
  
  # 列出可用密钥
  gpg --list-keys
  ```

### 错误：401 Unauthorized
- 原因：令牌无效或过期
- 解决：在 Central Portal 重新生成令牌

## 验证发布

1. 登录 https://central.sonatype.com
2. 查看 "Deployments" 部分
3. 等待验证完成（通常几分钟）
4. 发布后约 30 分钟可在 Maven Central 搜索到

## 重要提醒

- **一旦发布，不能删除或修改**：请仔细检查版本号和内容
- **GPG 签名是必需的**：确保所有文件都已签名
- **必需的 artifacts**：
  - 主 JAR 文件
  - sources JAR
  - javadoc JAR
  - POM 文件
  - 所有文件的 .asc 签名

## 更多信息

- 官方文档：https://central.sonatype.org/publish/publish-portal-maven/
- Central Portal：https://central.sonatype.com
- 支持邮箱：central-support@sonatype.com

```
 mvn clean deploy -s settings-central.xml
```