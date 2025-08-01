# AWS Bedrock 认证说明

## 认证方式

### 1. 标准 AWS IAM 凭证（推荐）

AWS Bedrock 使用标准的 AWS IAM 认证。你需要：

1. **AWS Access Key ID** - 格式类似：`AKIAIOSFODNN7EXAMPLE`
2. **AWS Secret Access Key** - 格式类似：`wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY`

#### 设置环境变量

```bash
export AWS_ACCESS_KEY_ID="your-access-key-id"
export AWS_SECRET_ACCESS_KEY="your-secret-access-key"
export AWS_REGION="us-east-1"  # 可选
```

#### 使用代码

```java
// 方式1：使用环境变量（自动读取）
OpenAI client = OpenAI.bedrock("us-east-1", "anthropic.claude-3-sonnet-20240229");

// 方式2：显式传入凭证
OpenAI client = OpenAI.bedrock("us-east-1", 
    "AKIAIOSFODNN7EXAMPLE", 
    "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
    "anthropic.claude-3-sonnet-20240229");
```

### 2. AWS 配置文件

在 `~/.aws/credentials` 文件中配置：

```ini
[default]
aws_access_key_id = your-access-key-id
aws_secret_access_key = your-secret-access-key

[bedrock-profile]
aws_access_key_id = another-access-key-id
aws_secret_access_key = another-secret-access-key
```

### 3. Bedrock API Key（特殊情况）

如果你有 Bedrock 特定的 API Key（格式如 `BedrockAPIKey-xxx`），这可能是：

1. **Bedrock Marketplace 模型的 API Key** - 用于访问特定的第三方模型
2. **临时访问令牌** - 有时效性的访问凭证

**注意**：标准 AWS SDK 可能不直接支持这种格式。你可能需要：

1. 联系 AWS 支持获取标准 IAM 凭证
2. 使用 AWS STS 将临时令牌转换为标准凭证
3. 检查是否需要特殊的认证端点

## 常见错误

### "The security token included in the request is invalid"

这个错误通常表示：

1. **凭证格式错误** - 确保使用正确的 AWS IAM 凭证格式
2. **凭证已过期** - 临时凭证可能已失效
3. **区域不匹配** - 确保凭证有权访问指定区域的 Bedrock
4. **权限不足** - IAM 用户需要有 Bedrock 相关权限

### 所需的 IAM 权限

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "bedrock:InvokeModel",
                "bedrock:InvokeModelWithResponseStream",
                "bedrock:ListFoundationModels"
            ],
            "Resource": "*"
        }
    ]
}
```

## 获取 AWS 凭证

1. **通过 AWS 控制台**：
   - 登录 [AWS Console](https://console.aws.amazon.com/)
   - 进入 IAM → Users → 选择用户 → Security credentials
   - 创建新的 Access Key

2. **通过 AWS CLI**：
   ```bash
   aws iam create-access-key --user-name your-username
   ```

## 支持的模型 ID

确保使用正确的模型 ID：

- `anthropic.claude-3-opus-20240229`
- `anthropic.claude-3-sonnet-20240229`
- `anthropic.claude-3-haiku-20240307`
- `anthropic.claude-v2:1`
- `anthropic.claude-v2`
- `anthropic.claude-instant-v1`
- `meta.llama2-70b-chat-v1`
- `meta.llama2-13b-chat-v1`
- `amazon.titan-text-express-v1`
- `amazon.titan-text-lite-v1`

## 测试连接

```java
public class TestBedrock {
    public static void main(String[] args) {
        try {
            // 列出可用模型（不需要调用 API）
            OpenAI client = OpenAI.bedrock("us-east-1", "anthropic.claude-3-sonnet-20240229");
            client.listModels().forEach(model -> {
                System.out.println(model.getId());
            });
        } catch (Exception e) {
            System.err.println("连接测试失败: " + e.getMessage());
        }
    }
}
```