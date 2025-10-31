package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Azure OpenAI Prompt Caching功能测试
 * 演示Azure的自动prompt caching功能,节省90-100%的输入token成本
 *
 * 运行方式:
 * mvn test -Dtest=AzurePromptCachingTest \
 *   -Dazure.apiKey=YOUR_API_KEY \
 *   -Dazure.resourceName=YOUR_RESOURCE \
 *   -Dazure.deploymentId=YOUR_DEPLOYMENT \
 *   -Dazure.apiVersion=2024-10-01-preview
 *
 * 注意:
 * 1. API版本必须是 2024-10-01-preview 或更新版本
 * 2. 支持的模型: gpt-4o, gpt-4o-mini, o1, o3-mini
 * 3. Azure会自动缓存重复的prompt前缀,无需手动配置
 * 4. 缓存最小要求: 前1024 tokens必须相同
 *
 * 成本节省:
 * - Standard部署: 缓存token享受~90%折扣
 * - Provisioned部署: 缓存token可能完全免费(最高100%折扣)
 */
public class AzurePromptCachingTest {
    private static final Logger logger = LoggerFactory.getLogger(AzurePromptCachingTest.class);

    // Azure配置 - 从系统属性读取
    private static final String API_KEY = System.getProperty("azure.apiKey", System.getenv("AZURE_OPENAI_API_KEY"));
    private static final String RESOURCE_NAME = System.getProperty("azure.resourceName", System.getenv("AZURE_OPENAI_RESOURCE"));
    private static final String DEPLOYMENT_ID = System.getProperty("azure.deploymentId", System.getenv("AZURE_OPENAI_DEPLOYMENT"));
    private static final String API_VERSION = System.getProperty("azure.apiVersion", "2024-10-01-preview");

    /**
     * 创建Azure OpenAI客户端
     */
    private OpenAI createClient() {
        if (API_KEY == null || RESOURCE_NAME == null || DEPLOYMENT_ID == null) {
            throw new IllegalStateException(
                "Azure配置缺失! 请设置:\n" +
                "  -Dazure.apiKey=YOUR_API_KEY\n" +
                "  -Dazure.resourceName=YOUR_RESOURCE\n" +
                "  -Dazure.deploymentId=YOUR_DEPLOYMENT\n" +
                "或设置环境变量: AZURE_OPENAI_API_KEY, AZURE_OPENAI_RESOURCE, AZURE_OPENAI_DEPLOYMENT"
            );
        }

        logger.info("创建Azure OpenAI客户端:");
        logger.info("  资源名: {}", RESOURCE_NAME);
        logger.info("  部署ID: {}", DEPLOYMENT_ID);
        logger.info("  API版本: {}", API_VERSION);

        return OpenAI.azure(API_KEY, RESOURCE_NAME, DEPLOYMENT_ID, API_VERSION);
    }

    /**
     * 测试1: 基本的Prompt Caching
     * Azure会自动缓存重复的prompt内容
     */
    @Test
    public void testBasicPromptCaching() {
        OpenAI client = createClient();

        // 准备长system prompt (1024+ tokens)
        String longSystemPrompt = "你是一个专业的AI技术助手,专注于帮助用户理解和应用人工智能技术。\n" +
"\n" +
"            你的职责包括:\n" +
"\n" +
"            1. 解释复杂的AI概念:\n" +
"               - 使用通俗易懂的语言\n" +
"               - 提供实际例子帮助理解\n" +
"               - 避免过度使用专业术语\n" +
"               - 当必须使用术语时,先解释其含义\n" +
"\n" +
"            2. 技术准确性:\n" +
"               - 确保所有信息准确无误\n" +
"               - 引用最新的研究和实践\n" +
"               - 区分理论和实践应用\n" +
"               - 及时更正任何误解\n" +
"\n" +
"            3. 结构化回答:\n" +
"               - 先给出简洁的总结性回答\n" +
"               - 然后提供详细的解释\n" +
"               - 使用编号列表或要点使内容清晰\n" +
"               - 在适当的地方添加示例代码或公式\n" +
"\n" +
"            4. 知识边界:\n" +
"               - 如果不确定某个信息,明确告知用户\n" +
"               - 建议用户查阅权威资源进行验证\n" +
"               - 承认AI技术的局限性\n" +
"               - 不要编造不存在的研究或数据\n" +
"\n" +
"            5. 教学方法:\n" +
"               - 从基础概念开始逐步深入\n" +
"               - 提供实际应用场景和案例\n" +
"               - 鼓励用户提出后续问题\n" +
"               - 帮助用户建立系统化的知识体系\n" +
"\n" +
"            6. 技术覆盖范围:\n" +
"               - 机器学习基础理论\n" +
"               - 深度学习架构(CNN, RNN, Transformer等)\n" +
"               - 自然语言处理技术\n" +
"               - 计算机视觉应用\n" +
"               - 强化学习原理\n" +
"               - 大语言模型(LLM)的工作原理\n" +
"               - AI伦理和安全问题\n" +
"               - 实际工程部署最佳实践\n" +
"\n" +
"            7. 代码示例:\n" +
"               - 优先使用Python作为示例语言\n" +
"               - 代码必须可运行且经过验证\n" +
"               - 添加详细的注释说明\n" +
"               - 展示最佳实践而非快捷方案\n" +
"\n" +
"            8. 数学公式:\n" +
"               - 使用LaTeX格式表示复杂公式\n" +
"               - 解释公式中每个符号的含义\n" +
"               - 提供直观的理解方式\n" +
"               - 在必要时给出推导过程\n" +
"\n" +
"            9. 前沿技术:\n" +
"               - 介绍最新的研究方向\n" +
"               - 讨论技术的潜在影响\n" +
"               - 分析优势和局限性\n" +
"               - 提供学习资源建议\n" +
"\n" +
"            10. 实践建议:\n" +
"                - 推荐合适的工具和框架\n" +
"                - 提供学习路线图\n" +
"                - 分享常见陷阱和解决方案\n" +
"                - 给出项目实践建议\n" +
"\n" +
"            在回答时,请始终保持专业、准确、友好的态度,帮助用户深入理解AI技术。\n" +
"            \n";

        logger.info("System prompt长度: {} 字符, 估算tokens: {} (最小要求: 1024)",
            longSystemPrompt.length(), estimateTokens(longSystemPrompt));

        // 第一次请求 - Azure自动创建缓存
        logger.info("\n=== 第一次请求 (Azure自动创建缓存) ===");
        ChatCompletionRequest request1 = new ChatCompletionRequest();
        request1.setMessages(Arrays.asList(
            ChatMessage.system(longSystemPrompt),
            ChatMessage.user("什么是机器学习?")
        ));
        request1.setMaxTokens(200);

        ChatCompletionResponse response1 = client.createChatCompletion(request1);
        logger.info("回答: {}", response1.getContent());
        printUsageStats(response1.getUsage());

        // 等待一会儿
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 第二次请求 - 应该命中缓存
        logger.info("\n=== 第二次请求 (应该命中Azure缓存) ===");
        ChatCompletionRequest request2 = new ChatCompletionRequest();
        request2.setMessages(Arrays.asList(
            ChatMessage.system(longSystemPrompt), // 相同的system prompt
            ChatMessage.user("什么是深度学习?") // 不同的问题
        ));
        request2.setMaxTokens(200);

        ChatCompletionResponse response2 = client.createChatCompletion(request2);
        logger.info("回答: {}", response2.getContent());
        printUsageStats(response2.getUsage());

        // 对比缓存效果
        logger.info("\n=== 缓存效果对比 ===");
        if (response1.getUsage() != null && response2.getUsage() != null) {
            Integer cached1 = response1.getUsage().getCacheReadInputTokens();
            Integer cached2 = response2.getUsage().getCacheReadInputTokens();

            logger.info("第1次请求 - 缓存命中tokens: {}", cached1 != null ? cached1 : 0);
            logger.info("第2次请求 - 缓存命中tokens: {}", cached2 != null ? cached2 : 0);

            if (cached2 != null && cached2 > 0) {
                logger.info("✅ Azure自动缓存工作正常!");
                double savings = cached2 * 0.9;
                logger.info("💰 第2次请求节省约 {} tokens的成本", Math.round(savings));
            } else {
                logger.warn("⚠️  未检测到缓存命中,可能原因:");
                logger.warn("   1. API版本不支持缓存(需要2024-10-01-preview+)");
                logger.warn("   2. 模型不支持缓存(需要gpt-4o/gpt-4o-mini/o1等)");
                logger.warn("   3. 内容不足1024 tokens");
                logger.warn("   4. 两次请求间隔太长(>5-10分钟)");
            }
        }
    }

    /**
     * 测试2: 流式响应中的Prompt Caching
     */
    @Test
    public void testStreamingWithCaching() throws InterruptedException {
        OpenAI client = createClient();

        String longSystemPrompt = "你是一个专业的编程助手,擅长Java、Python、Go、Rust等多种编程语言开发。\n" +
"            你的核心职责是帮助开发者解决技术问题,提供高质量的代码示例和最佳实践建议。\n" +
"\n" +
"            在回答时,请严格遵循以下原则:\n" +
"\n" +
"            1. 代码质量标准:\n" +
"               - 代码必须遵循行业最佳实践\n" +
"               - 添加详细的注释说明每个关键步骤\n" +
"               - 考虑性能优化和资源使用\n" +
"               - 重视安全性,避免常见漏洞\n" +
"               - 提供完整可运行的示例代码\n" +
"\n" +
"            2. 技术解释:\n" +
"               - 解释关键技术点和设计决策\n" +
"               - 指出常见陷阱和注意事项\n" +
"               - 说明为什么这样设计更好\n" +
"               - 提供替代方案的对比分析\n" +
"\n" +
"            3. 工具和生态:\n" +
"               - 推荐相关的工具和库\n" +
"               - 说明版本兼容性问题\n" +
"               - 给出依赖管理建议\n" +
"               - 介绍社区最佳实践\n" +
"\n" +
"            4. 性能优化:\n" +
"               - 给出代码优化建议\n" +
"               - 分析时间和空间复杂度\n" +
"               - 提供性能测试方法\n" +
"               - 说明优化的优先级\n" +
"\n" +
"            5. 软件工程:\n" +
"               - 考虑可维护性和可测试性\n" +
"               - 遵循SOLID原则\n" +
"               - 应用合适的设计模式\n" +
"               - 关注代码可读性\n" +
"\n" +
"            6. 并发编程:\n" +
"               - 正确处理线程安全问题\n" +
"               - 避免死锁和竞态条件\n" +
"               - 使用合适的同步机制\n" +
"               - 考虑并发性能优化\n" +
"\n" +
"            7. 错误处理:\n" +
"               - 实现健壮的异常处理\n" +
"               - 提供有意义的错误信息\n" +
"               - 考虑边界情况和异常场景\n" +
"               - 遵循语言的错误处理惯例\n" +
"\n" +
"            8. 测试策略:\n" +
"               - 建议单元测试方法\n" +
"               - 提供测试用例示例\n" +
"               - 说明测试覆盖率要求\n" +
"               - 推荐测试框架和工具\n" +
"\n" +
"            请始终以专业、耐心、友好的态度回答问题,帮助开发者提升技术能力。\n" +
"            \n";

        logger.info("System prompt长度: {} 字符, 估算tokens: {}",
            longSystemPrompt.length(), estimateTokens(longSystemPrompt));

        // 第一次流式请求
        logger.info("\n=== 第一次流式请求 ===");
        ChatCompletionRequest request1 = new ChatCompletionRequest();
        request1.setMessages(Arrays.asList(
            ChatMessage.system(longSystemPrompt),
            ChatMessage.user("如何实现线程安全的单例模式?")
        ));
        request1.setMaxTokens(200);

        CountDownLatch latch1 = new CountDownLatch(1);
        StringBuilder response1Text = new StringBuilder();
        ChatCompletionResponse.Usage[] usage1 = new ChatCompletionResponse.Usage[1];

        client.createChatCompletionStream(
            request1,
            chunk -> {
                String content = chunk.getContent();
                if (content != null) {
                    System.out.print(content);
                    response1Text.append(content);
                }
                if (chunk.getUsage() != null) {
                    usage1[0] = chunk.getUsage();
                }
            },
            () -> {
                System.out.println("\n");
                logger.info("流式响应完成,字符数: {}", response1Text.length());
                if (usage1[0] != null) {
                    printUsageStats(usage1[0]);
                }
                latch1.countDown();
            },
            error -> {
                logger.error("流式请求错误", error);
                latch1.countDown();
            }
        );

        latch1.await();
        Thread.sleep(1000);

        // 第二次流式请求
        logger.info("\n=== 第二次流式请求 (应该命中缓存) ===");
        ChatCompletionRequest request2 = new ChatCompletionRequest();
        request2.setMessages(Arrays.asList(
            ChatMessage.system(longSystemPrompt), // 相同
            ChatMessage.user("volatile关键字的作用是什么?") // 不同
        ));
        request2.setMaxTokens(200);

        CountDownLatch latch2 = new CountDownLatch(1);
        StringBuilder response2Text = new StringBuilder();
        ChatCompletionResponse.Usage[] usage2 = new ChatCompletionResponse.Usage[1];

        client.createChatCompletionStream(
            request2,
            chunk -> {
                String content = chunk.getContent();
                if (content != null) {
                    System.out.print(content);
                    response2Text.append(content);
                }
                if (chunk.getUsage() != null) {
                    usage2[0] = chunk.getUsage();
                }
            },
            () -> {
                System.out.println("\n");
                logger.info("流式响应完成,字符数: {}", response2Text.length());
                if (usage2[0] != null) {
                    printUsageStats(usage2[0]);
                }
                latch2.countDown();
            },
            error -> {
                logger.error("流式请求错误", error);
                latch2.countDown();
            }
        );

        latch2.await();

        // 对比
        logger.info("\n=== 流式缓存效果对比 ===");
        if (usage1[0] != null && usage2[0] != null) {
            logger.info("第1次 - 缓存tokens: {}", usage1[0].getCacheReadInputTokens());
            logger.info("第2次 - 缓存tokens: {}", usage2[0].getCacheReadInputTokens());

            if (usage2[0].getCacheReadInputTokens() != null && usage2[0].getCacheReadInputTokens() > 0) {
                logger.info("✅ 流式响应中Azure缓存工作正常!");
            }
        }
    }

    /**
     * 测试3: 多轮对话中的缓存
     */
    @Test
    public void testMultiTurnConversationCaching() {
        OpenAI client = createClient();

        // 构建长对话历史
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(ChatMessage.system("你是一个专业的技术顾问,专注于帮助企业进行数字化转型。你的建议应该考虑技术可行性、成本效益、实施难度和长期维护等多个方面。"));

        conversation.add(ChatMessage.user("我们公司想要实施微服务架构,有什么建议?"));
        conversation.add(ChatMessage.assistant(
            "微服务架构是一个重要的技术决策,需要仔细评估:\n\n" +
            "优势:\n" +
            "1. 独立部署和扩展\n" +
            "2. 技术栈灵活性\n" +
            "3. 团队自治\n" +
            "4. 故障隔离\n\n" +
            "挑战:\n" +
            "1. 分布式系统复杂性\n" +
            "2. 运维成本增加\n" +
            "3. 数据一致性问题\n" +
            "4. 服务间通信开销\n\n" +
            "建议从单个模块开始逐步拆分,而不是一次性重构整个系统。"
        ));

        conversation.add(ChatMessage.user("那应该如何选择服务拆分的粒度?"));
        conversation.add(ChatMessage.assistant(
            "服务拆分粒度的选择应该遵循以下原则:\n\n" +
            "1. 业务边界清晰\n" +
            "2. 高内聚低耦合\n" +
            "3. 独立部署价值\n" +
            "4. 团队规模匹配\n\n" +
            "避免过度拆分导致的'纳米服务'问题,也要避免服务过大失去微服务的优势。" +
            "一般建议一个服务由2-8人的团队负责,代码量在几万到十几万行之间。"
        ));

        logger.info("对话历史: {} 条消息", conversation.size());

        // 第一次请求
        logger.info("\n=== 第一次请求 (建立缓存) ===");
        List<ChatMessage> messages1 = new ArrayList<>(conversation);
        messages1.add(ChatMessage.user("如何处理服务间的数据一致性?"));

        ChatCompletionRequest request1 = new ChatCompletionRequest();
        request1.setMessages(messages1);
        request1.setMaxTokens(200);

        ChatCompletionResponse response1 = client.createChatCompletion(request1);
        logger.info("回答: {}", response1.getContent());
        printUsageStats(response1.getUsage());

        // 第二次请求 - 相同历史
        logger.info("\n=== 第二次请求 (应该命中缓存) ===");
        List<ChatMessage> messages2 = new ArrayList<>(conversation);
        messages2.add(ChatMessage.user("需要使用API网关吗?"));

        ChatCompletionRequest request2 = new ChatCompletionRequest();
        request2.setMessages(messages2);
        request2.setMaxTokens(200);

        ChatCompletionResponse response2 = client.createChatCompletion(request2);
        logger.info("回答: {}", response2.getContent());
        printUsageStats(response2.getUsage());

        // 对比
        logger.info("\n=== 多轮对话缓存效果 ===");
        if (response1.getUsage() != null && response2.getUsage() != null) {
            Integer cached2 = response2.getUsage().getCacheReadInputTokens();
            if (cached2 != null && cached2 > 0) {
                logger.info("✅ 对话历史缓存工作正常! 缓存了 {} tokens", cached2);
            }
        }
    }

    /**
     * 估算文本的token数量 (粗略估计: 1 token ≈ 4个字符)
     */
    private int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / 4;
    }

    /**
     * 打印使用统计
     */
    private void printUsageStats(ChatCompletionResponse.Usage usage) {
        logger.info("Token使用统计:");
        logger.info("  输入tokens: {}", usage.getPromptTokens());
        logger.info("  输出tokens: {}", usage.getCompletionTokens());
        logger.info("  总计tokens: {}", usage.getTotalTokens());

        // Azure缓存统计
        Integer cachedTokens = usage.getCacheReadInputTokens();
        if (cachedTokens != null && cachedTokens > 0) {
            logger.info("  ✅ 缓存命中tokens: {} (节省约90%成本!)", cachedTokens);
            double savings = cachedTokens * 0.9;
            logger.info("  💰 估算节省: ~{} tokens的成本 (约${} at $0.005/1K for gpt-4o)",
                Math.round(savings),
                String.format("%.6f", savings * 0.005 / 1000));
        } else {
            logger.info("  ℹ️  未命中缓存 (可能是首次请求或内容不足1024 tokens)");
        }

        // 显示Azure特有的详细信息
        if (usage.getPromptTokensDetails() != null) {
            ChatCompletionResponse.PromptTokensDetails details = usage.getPromptTokensDetails();
            logger.info("  Azure详细信息:");
            if (details.getCachedTokens() != null) {
                logger.info("    - cached_tokens: {}", details.getCachedTokens());
            }
            if (details.getAudioTokens() != null) {
                logger.info("    - audio_tokens: {}", details.getAudioTokens());
            }
        }

        if (usage.getCompletionTokensDetails() != null) {
            ChatCompletionResponse.CompletionTokensDetails details = usage.getCompletionTokensDetails();
            if (details.getReasoningTokens() != null) {
                logger.info("    - reasoning_tokens: {}", details.getReasoningTokens());
            }
        }
    }
}
