# 设置 Personal Access Token (PAT) 

如果默认的 GITHUB_TOKEN 权限不足，可以使用 Personal Access Token。

## 创建 PAT

1. 访问 GitHub Settings: https://github.com/settings/tokens/new

2. 创建 Classic Token，勾选以下权限：
   - `repo` (全部)
   - `write:packages`
   - `read:packages`

3. 设置有效期（建议 90 天）

4. 点击 "Generate token"

5. **立即复制 token**（只显示一次）

## 添加到 Repository Secrets

1. 访问: https://github.com/twwch/openai-sdk/settings/secrets/actions

2. 点击 "New repository secret"

3. 添加：
   - Name: `RELEASE_TOKEN`
   - Value: 您刚才复制的 token

## 更新 Workflow

在 workflow 中使用新的 token：

```yaml
- name: Create GitHub Release
  uses: ncipollo/release-action@v1
  with:
    # ... 其他配置 ...
    token: ${{ secrets.RELEASE_TOKEN }}  # 使用 PAT 而不是 GITHUB_TOKEN
```

## 安全建议

- 定期轮换 token（每 90 天）
- 只给必要的权限
- 不要在代码中硬编码 token