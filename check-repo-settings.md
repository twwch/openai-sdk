# 检查 GitHub 仓库设置

## 1. 检查 Actions 权限

访问: https://github.com/twwch/openai-sdk/settings/actions

在 "Workflow permissions" 部分：
- 选择 "Read and write permissions"
- 勾选 "Allow GitHub Actions to create and approve pull requests"
- 点击 "Save"

## 2. 检查是否是 Fork 仓库

如果这是一个 fork 的仓库，GitHub Actions 的权限会受限。

## 3. 检查分支保护规则

访问: https://github.com/twwch/openai-sdk/settings/branches

如果有分支保护规则，可能需要：
- 添加例外允许 GitHub Actions 创建 release
- 或者使用 Personal Access Token

## 快速测试

创建一个简单的 workflow 测试权限：

```yaml
name: Test Permissions

on:
  workflow_dispatch:

jobs:
  test:
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
    - uses: actions/checkout@v4
    - name: Test Release Creation
      uses: ncipollo/release-action@v1
      with:
        tag: test-v0.0.1
        name: Test Release
        body: Testing permissions
        draft: true
        token: ${{ secrets.GITHUB_TOKEN }}
```