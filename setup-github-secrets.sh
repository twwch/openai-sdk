#!/bin/bash

# 设置颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}GitHub Actions 自动发布配置脚本${NC}"
echo "======================================"

# 检查 GPG 密钥
echo -e "\n${YELLOW}步骤 1: 检查 GPG 密钥${NC}"
GPG_KEY_ID="BE3FD396CE4198EF"
if gpg --list-secret-keys --keyid-format LONG | grep -q "$GPG_KEY_ID"; then
    echo -e "${GREEN}✓ 找到 GPG 密钥: $GPG_KEY_ID${NC}"
else
    echo -e "${RED}✗ 未找到 GPG 密钥: $GPG_KEY_ID${NC}"
    echo "请先生成 GPG 密钥"
    exit 1
fi

# 导出 GPG 私钥
echo -e "\n${YELLOW}步骤 2: 导出 GPG 私钥${NC}"
echo "请输入您的 GPG 密码："
GPG_PRIVATE_KEY=$(gpg --export-secret-keys $GPG_KEY_ID | base64)

if [ -z "$GPG_PRIVATE_KEY" ]; then
    echo -e "${RED}✗ GPG 私钥导出失败${NC}"
    exit 1
fi

echo -e "${GREEN}✓ GPG 私钥导出成功${NC}"

# 创建 GitHub Secrets 配置文件
echo -e "\n${YELLOW}步骤 3: 生成 GitHub Secrets 配置${NC}"
cat > github-secrets.txt << EOF
====================================
GitHub Secrets 配置
====================================

请在 GitHub 仓库的 Settings > Secrets and variables > Actions 中添加以下 Secrets：

1. GPG_PRIVATE_KEY:
$(echo "$GPG_PRIVATE_KEY" | head -n 3)...
[内容已截断，完整内容请查看 github-secrets.txt]

2. GPG_PASSPHRASE:
[请输入您的 GPG 密码]

3. MAVEN_USERNAME:
id6DRn

4. MAVEN_PASSWORD:
040zh3o9he7bLLzPGXThbI064qQhnP9xQ

====================================
EOF

# 保存完整的 GPG 私钥到单独文件
echo "$GPG_PRIVATE_KEY" > gpg-private-key-base64.txt

echo -e "${GREEN}✓ 配置文件已生成${NC}"
echo -e "\n${YELLOW}重要文件：${NC}"
echo "1. github-secrets.txt - GitHub Secrets 配置摘要"
echo "2. gpg-private-key-base64.txt - 完整的 GPG 私钥（base64）"
echo -e "\n${RED}警告：请在配置完成后立即删除这些文件！${NC}"

# 显示下一步操作
echo -e "\n${YELLOW}下一步操作：${NC}"
echo "1. 访问 https://github.com/twwch/openai-sdk/settings/secrets/actions"
echo "2. 添加上述 4 个 Secrets"
echo "3. 删除生成的配置文件："
echo "   rm github-secrets.txt gpg-private-key-base64.txt"
echo "4. 创建并推送 tag 进行测试："
echo "   git tag -a v1.0.1 -m \"Test release\""
echo "   git push origin v1.0.1"