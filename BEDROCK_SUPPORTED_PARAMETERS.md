# AWS Bedrock Claude 支持的参数

## 官方支持的参数（基于AWS文档）

### 必需参数
1. `anthropic_version` - 必须为 "bedrock-2023-05-31"
2. `max_tokens` - 生成的最大token数
3. `messages` - 对话消息数组

### 可选参数
1. `system` - 系统提示词
2. `temperature` - 0-1之间，控制随机性（默认：1）
3. `top_p` - 0-1之间，nucleus采样（默认：0.999）
4. `top_k` - 0-500之间，从前K个token中采样（默认：禁用）
5. `stop_sequences` - 停止序列数组
6. `tools` - 工具定义（需要Claude 3模型）
7. `tool_choice` - 工具使用策略
8. `anthropic_beta` - Beta功能标头列表

## 不支持的OpenAI参数

以下参数会导致METRIC_VALUES错误，必须过滤掉：

1. `n` - 生成数量
2. `presence_penalty` - 存在惩罚
3. `frequency_penalty` - 频率惩罚
4. `logprobs` - 日志概率
5. `service_tier` - 服务层级
6. `logit_bias` - logit偏差
7. `user` - 用户标识
8. `response_format` - 响应格式
9. `audio` - 音频参数
10. `max_completion_tokens` - 最大完成token数
11. `metadata` - 元数据
12. `modalities` - 模态
13. `parallel_tool_calls` - 并行工具调用
14. `prediction` - 预测
15. `prompt_cache_key` - 提示缓存键
16. `reasoning_effort` - 推理努力
17. `safety_identifier` - 安全标识符
18. `seed` - 种子
19. `store` - 存储
20. `top_logprobs` - 顶部日志概率
21. `web_search_options` - 网络搜索选项

## 参数值范围

- `temperature`: 0.0 - 1.0
- `top_p`: 0.0 - 1.0
- `top_k`: 0 - 500
- `max_tokens`: 根据模型不同有不同限制

## 建议

1. 在发送请求到Bedrock前，清除所有不支持的参数
2. 只设置必需参数和明确需要的可选参数
3. 不要包含默认值（如temperature=1.0）
4. 使用stream_options时要谨慎，确保模型支持