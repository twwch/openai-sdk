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
import java.util.stream.Collectors;

/**
 * Bedrock Prompt Caching功能测试
 * 演示如何使用prompt caching来节省90%的输入token成本
 *
 * 运行方式:
 * 1. 使用默认凭证链:
 *    mvn test -Dtest=BedrockPromptCachingTest
 *
 * 2. 使用显式凭证:
 *    mvn test -Dtest=BedrockPromptCachingTest \
 *      -Dbedrock.accessKeyId=YOUR_KEY \
 *      -Dbedrock.secretAccessKey=YOUR_SECRET
 *
 * 3. 自定义区域和模型:
 *    mvn test -Dtest=BedrockPromptCachingTest \
 *      -Dbedrock.region=us-west-2 \
 *      -Dbedrock.modelId=anthropic.claude-3-7-sonnet-20250219-v1:0
 */
public class BedrockPromptCachingTest {
    private static final Logger logger = LoggerFactory.getLogger(BedrockPromptCachingTest.class);

    // 测试配置
    private static final String REGION = System.getProperty("bedrock.region", "us-west-2");
    private static final String MODEL_ID = System.getProperty("bedrock.modelId", "us.anthropic.claude-3-7-sonnet-20250219-v1:0");
    private static final String ACCESS_KEY_ID = System.getProperty("bedrock.accessKeyId", System.getenv("AWS_ACCESS_KEY_ID"));
    private static final String SECRET_ACCESS_KEY = System.getProperty("bedrock.secretAccessKey", System.getenv("AWS_SECRET_ACCESS_KEY"));

    /**
     * 创建OpenAI客户端
     */
    private OpenAI createClient() {
        if (ACCESS_KEY_ID != null && SECRET_ACCESS_KEY != null) {
            logger.info("使用显式提供的AWS凭证");
            return OpenAI.bedrock(REGION, ACCESS_KEY_ID, SECRET_ACCESS_KEY, MODEL_ID);
        } else {
            logger.info("使用默认AWS凭证链（~/.aws/credentials, 环境变量, IAM角色等）");
            return OpenAI.bedrock(REGION, MODEL_ID);
        }
    }

    /**
     * 测试1: 使用system消息缓存
     * 通过启用bedrockEnableSystemCache来缓存系统提示
     */
    @Test
    public void testSystemMessageCaching() {
        OpenAI client = createClient();
        logger.info("测试配置: 区域={}, 模型={}", REGION, MODEL_ID);

        // 创建一个长的system消息用于缓存 (确保超过1024 tokens)
        String longSystemPrompt = "\n" +
"            你是一个专业的AI助手,专门帮助用户理解和学习人工智能相关知识。\n" +
"\n" +
"            你需要遵循以下详细规则:\n" +
"\n" +
"            1. 回答准确性要求:\n" +
"               - 所有技术概念必须准确无误\n" +
"               - 引用最新的研究成果和行业标准\n" +
"               - 如果信息可能过时,请明确说明\n" +
"               - 避免使用模糊或不确定的表述\n" +
"\n" +
"            2. 沟通风格:\n" +
"               - 使用友好、专业的语气\n" +
"               - 根据用户背景调整解释深度\n" +
"               - 使用类比和例子帮助理解复杂概念\n" +
"               - 避免过度使用术语,必要时提供解释\n" +
"\n" +
"            3. 内容结构:\n" +
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
"            11. 深度学习框架:\n" +
"                - TensorFlow和Keras的使用场景\n" +
"                - PyTorch的优势和特点\n" +
"                - JAX在研究中的应用\n" +
"                - 不同框架的性能对比\n" +
"                - 模型训练和优化技巧\n" +
"                - 分布式训练策略\n" +
"\n" +
"            12. 数据处理:\n" +
"                - 数据预处理的重要性\n" +
"                - 特征工程最佳实践\n" +
"                - 数据增强技术\n" +
"                - 处理不平衡数据集\n" +
"                - 数据标注质量控制\n" +
"                - 大规模数据集管理\n" +
"\n" +
"            13. 模型评估:\n" +
"                - 选择合适的评估指标\n" +
"                - 交叉验证方法\n" +
"                - 过拟合和欠拟合的识别\n" +
"                - 模型诊断技术\n" +
"                - A/B测试和在线评估\n" +
"                - 模型可解释性分析\n" +
"\n" +
"            14. 优化技术:\n" +
"                - SGD和Adam等优化器比较\n" +
"                - 学习率调度策略\n" +
"                - 批量大小的影响\n" +
"                - 梯度裁剪和归一化\n" +
"                - 正则化方法(L1/L2/Dropout)\n" +
"                - 超参数调优方法\n" +
"\n" +
"            15. 模型部署:\n" +
"                - 模型压缩和量化\n" +
"                - 推理优化技术\n" +
"                - 容器化部署方案\n" +
"                - 模型版本管理\n" +
"                - 监控和日志记录\n" +
"                - 性能和成本优化\n" +
"\n" +
"            16. 安全和隐私:\n" +
"                - 对抗样本防御\n" +
"                - 模型安全性测试\n" +
"                - 差分隐私技术\n" +
"                - 联邦学习应用\n" +
"                - 数据脱敏方法\n" +
"                - 模型水印和版权保护\n" +
"\n" +
"            17. 行业应用:\n" +
"                - 医疗健康领域的AI应用\n" +
"                - 金融科技中的机器学习\n" +
"                - 自动驾驶技术栈\n" +
"                - 智能制造和工业4.0\n" +
"                - 推荐系统设计\n" +
"                - 语音识别和合成\n" +
"\n" +
"            18. 研究前沿:\n" +
"                - Transformer架构的演进\n" +
"                - 多模态学习趋势\n" +
"                - 小样本学习技术\n" +
"                - 神经架构搜索(NAS)\n" +
"                - 自监督学习方法\n" +
"                - 因果推理和AI\n" +
"\n" +
"            19. 开发工具链:\n" +
"                - Jupyter和VS Code配置\n" +
"                - Git版本控制最佳实践\n" +
"                - Docker容器化开发\n" +
"                - MLOps工具和流程\n" +
"                - 实验跟踪系统(MLflow/W&B)\n" +
"                - 代码质量和测试\n" +
"\n" +
"            20. 学习资源:\n" +
"                - 推荐的在线课程和教材\n" +
"                - 重要的学术会议和期刊\n" +
"                - 开源项目和代码库\n" +
"                - 技术博客和社区\n" +
"                - 数据集和竞赛平台\n" +
"                - 持续学习的建议\n" +
"\n" +
"            在回答时,请始终保持专业、准确、友好的态度,帮助用户深入理解AI技术。\n" +
"            对于复杂问题,建议分步骤解答,确保用户能够充分理解每个环节。\n" +
"            \n";

        logger.info("System prompt长度: {} 字符, 估算tokens: {} (最小要求: 1024)",
            longSystemPrompt.length(), estimateTokens(longSystemPrompt));

        // 第一次请求 - 创建缓存
        ChatCompletionRequest request1 = new ChatCompletionRequest();
        request1.setModel(MODEL_ID);
        request1.setBedrockEnableSystemCache(true); // 启用system缓存
        request1.setMessages(Arrays.asList(
                ChatMessage.system(longSystemPrompt),
                ChatMessage.user("什么是机器学习?")
        ));

        logger.info("=== 第一次请求 (创建缓存) ===");
        ChatCompletionResponse response1 = client.createChatCompletion(request1);
        logger.info("回答: {}", response1.getContent());
        printUsageStats(response1.getUsage());

        // 等待一会儿,然后发送第二次请求 - 命中缓存
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ChatCompletionRequest request2 = new ChatCompletionRequest();
        request2.setModel(MODEL_ID);
        request2.setBedrockEnableSystemCache(true);
        request2.setMessages(Arrays.asList(
                ChatMessage.system(longSystemPrompt), // 相同的system消息
                ChatMessage.user("什么是深度学习?") // 不同的用户问题
        ));

        logger.info("\n=== 第二次请求 (应该命中缓存) ===");
        ChatCompletionResponse response2 = client.createChatCompletion(request2);
        logger.info("回答: {}", response2.getContent());
        printUsageStats(response2.getUsage());
    }

    /**
     * 测试2: 使用ContentPart的cache_control
     * 可以在用户消息中精确控制哪些内容需要缓存
     */
    @Test
    public void testContentPartCaching() {
        OpenAI client = createClient();
        logger.info("测试配置: 区域={}, 模型={}", REGION, MODEL_ID);

        // 创建一个包含长文本的消息,并标记为可缓存 (超过1024 tokens的技术文档)
        String longContext = "\n" +
"            # 大语言模型(LLM)技术白皮书\n" +
"\n" +
"            ## 1. 引言\n" +
"\n" +
"            大语言模型(Large Language Models, LLMs)是近年来人工智能领域最重要的突破之一。\n" +
"            这些模型通过在海量文本数据上进行训练,学习到了丰富的语言知识和世界知识,\n" +
"            能够执行各种自然语言处理任务,包括文本生成、问答、翻译、摘要等。\n" +
"\n" +
"            ## 2. 技术架构\n" +
"\n" +
"            ### 2.1 Transformer架构\n" +
"            现代LLM主要基于Transformer架构,该架构由Vaswani等人在2017年的论文\n" +
"            \"Attention is All You Need\"中首次提出。Transformer的核心创新是自注意力机制\n" +
"            (Self-Attention),它允许模型在处理序列时关注不同位置的相关信息。\n" +
"\n" +
"            关键组件:\n" +
"            - Multi-Head Attention: 多头注意力机制允许模型从不同的表示子空间学习信息\n" +
"            - Position Encoding: 位置编码为序列中的每个位置添加位置信息\n" +
"            - Feed-Forward Networks: 前馈神经网络在每个位置独立应用\n" +
"            - Layer Normalization: 层归一化稳定训练过程\n" +
"            - Residual Connections: 残差连接帮助梯度流动\n" +
"\n" +
"            ### 2.2 预训练目标\n" +
"            大多数LLM使用自回归语言建模作为预训练目标,即预测序列中的下一个token。\n" +
"            这种简单但强大的目标使模型能够学习语言的统计规律和语义知识。\n" +
"\n" +
"            数学表示:\n" +
"            给定序列 x1, x2, ..., xn，模型学习条件概率分布:\n" +
"            P(xi | x1, x2, ..., xi-1)\n" +
"\n" +
"            ## 3. 训练方法\n" +
"\n" +
"            ### 3.1 预训练阶段\n" +
"            - 数据规模: 通常使用TB级别的文本数据\n" +
"            - 训练时间: 可能需要数周到数月的GPU集群训练\n" +
"            - 优化器: 通常使用Adam或AdamW优化器\n" +
"            - 学习率调度: 采用warmup和衰减策略\n" +
"\n" +
"            ### 3.2 微调阶段\n" +
"            在特定任务上进行微调可以显著提升模型在该任务上的性能:\n" +
"            - 监督微调(Supervised Fine-Tuning, SFT)\n" +
"            - 人类反馈强化学习(RLHF)\n" +
"            - 指令微调(Instruction Tuning)\n" +
"\n" +
"            ## 4. 模型规模演进\n" +
"\n" +
"            模型参数量的增长趋势:\n" +
"            - GPT-2 (2019): 1.5B参数\n" +
"            - GPT-3 (2020): 175B参数\n" +
"            - PaLM (2022): 540B参数\n" +
"            - GPT-4 (2023): 参数量未公开,但估计超过1T\n" +
"\n" +
"            ## 5. 涌现能力(Emergent Abilities)\n" +
"\n" +
"            当模型规模达到一定程度时,会出现一些在小模型中不存在的能力:\n" +
"            - 上下文学习(In-Context Learning): 无需梯度更新即可从示例中学习\n" +
"            - 思维链推理(Chain-of-Thought): 通过逐步推理解决复杂问题\n" +
"            - 指令遵循: 理解并执行自然语言指令\n" +
"\n" +
"            ## 6. 主要挑战\n" +
"\n" +
"            ### 6.1 计算成本\n" +
"            训练大型模型需要巨大的计算资源,限制了研究和应用的可及性。\n" +
"\n" +
"            ### 6.2 幻觉问题\n" +
"            模型可能生成看似合理但实际上不准确的信息,需要通过检索增强等方法缓解。\n" +
"\n" +
"            ### 6.3 对齐问题\n" +
"            确保模型的行为符合人类价值观和意图是一个重要挑战。\n" +
"\n" +
"            ## 7. 应用场景\n" +
"\n" +
"            - 智能客服和对话系统\n" +
"            - 内容创作和编辑\n" +
"            - 代码生成和调试\n" +
"            - 教育辅导\n" +
"            - 科研辅助\n" +
"            - 多语言翻译\n" +
"            - 信息抽取和知识图谱构建\n" +
"\n" +
"            ## 8. 未来方向\n" +
"\n" +
"            - 多模态融合: 整合文本、图像、音频等多种模态\n" +
"            - 效率优化: 模型压缩、量化、蒸馏等技术\n" +
"            - 可解释性: 理解模型的决策过程\n" +
"            - 安全性: 防止恶意使用和偏见\n" +
"            - 个性化: 适应特定用户和领域的需求\n" +
"\n" +
"            ## 9. 结论\n" +
"\n" +
"            大语言模型代表了AI技术的重大进步,但仍有许多问题需要解决。\n" +
"            未来的研究将专注于提高模型的可靠性、效率和安全性,同时探索新的应用场景。\n" +
"            \n";

        logger.info("Context长度: {} 字符, 估算tokens: {} (最小要求: 1024)",
            longContext.length(), estimateTokens(longContext));

        // 第一次请求 - 创建缓存
        ChatCompletionRequest request1 = new ChatCompletionRequest();
        request1.setModel(MODEL_ID);
        request1.setMessages(Arrays.asList(
                ChatMessage.user(
                        ChatMessage.ContentPart.textWithCache(longContext, true), // 启用缓存
                        ChatMessage.ContentPart.text("\n\n基于上述内容,总结一下主要观点。")
                )
        ));

        logger.info("=== 第一次请求 (创建缓存) ===");
        ChatCompletionResponse response1 = client.createChatCompletion(request1);
        logger.info("回答: {}", response1.getContent());
        printUsageStats(response1.getUsage());

        // 第二次请求 - 命中缓存
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ChatCompletionRequest request2 = new ChatCompletionRequest();
        request2.setModel(MODEL_ID);
        request2.setMessages(Arrays.asList(
                ChatMessage.user(
                        ChatMessage.ContentPart.textWithCache(longContext, true), // 相同的缓存内容
                        ChatMessage.ContentPart.text("\n\n请提取三个关键要点。")
                )
        ));

        logger.info("\n=== 第二次请求 (应该命中缓存) ===");
        ChatCompletionResponse response2 = client.createChatCompletion(request2);
        logger.info("回答: {}", response2.getContent());
        printUsageStats(response2.getUsage());
    }

    /**
     * 测试3: 组合使用 - system缓存 + content缓存
     * 在实际应用中,可以同时缓存system prompt和用户上下文
     */
    @Test
    public void testCombinedCaching() {
        OpenAI client = createClient();
        logger.info("测试配置: 区域={}, 模型={}", REGION, MODEL_ID);

        // 长的代码审查规则 (超过1024 tokens)
        String systemPrompt = "\n" +
"            你是一个资深的代码审查助手,需要严格遵循以下代码规范和最佳实践:\n" +
"\n" +
"            1. 代码质量标准:\n" +
"               - 代码必须清晰、可读、易于维护\n" +
"               - 遵循单一职责原则\n" +
"               - 避免代码重复(DRY原则)\n" +
"               - 保持函数和类的粒度适中\n" +
"\n" +
"            2. 命名规范:\n" +
"               - 类名使用PascalCase\n" +
"               - 方法和变量使用camelCase\n" +
"               - 常量使用UPPER_SNAKE_CASE\n" +
"               - 命名要有意义,避免使用缩写\n" +
"\n" +
"            3. 注释要求:\n" +
"               - 公共API必须有详细的JavaDoc\n" +
"               - 复杂逻辑需要添加解释性注释\n" +
"               - 避免无意义的注释\n" +
"               - 保持注释与代码同步更新\n" +
"\n" +
"            4. 错误处理:\n" +
"               - 不要吞掉异常\n" +
"               - 使用合适的异常类型\n" +
"               - 提供有意义的错误信息\n" +
"               - 考虑使用Optional处理null值\n" +
"\n" +
"            5. 性能考虑:\n" +
"               - 避免不必要的对象创建\n" +
"               - 合理使用集合类型\n" +
"               - 注意循环中的性能瓶颈\n" +
"               - 考虑使用缓存机制\n" +
"\n" +
"            6. 安全性检查:\n" +
"               - 验证所有外部输入\n" +
"               - 防止SQL注入和XSS攻击\n" +
"               - 不要在代码中硬编码敏感信息\n" +
"               - 使用参数化查询\n" +
"\n" +
"            7. 测试覆盖:\n" +
"               - 关键逻辑必须有单元测试\n" +
"               - 测试用例要覆盖边界条件\n" +
"               - 使用有意义的测试方法命名\n" +
"               - 保持测试代码的可维护性\n" +
"            \n";

        // 长的Java代码示例 (超过1024 tokens)
        String codeContext = "\n" +
"            package com.example.service;\n" +
"\n" +
"            import java.util.*;\n" +
"            import java.util.concurrent.*;\n" +
"            import java.util.stream.Collectors;\n" +
"            import org.slf4j.Logger;\n" +
"            import org.slf4j.LoggerFactory;\n" +
"\n" +
"            /**\n" +
"             * 用户服务实现类\n" +
"             * 提供用户管理的核心功能\n" +
"             */\n" +
"            public class UserServiceImpl implements UserService {\n" +
"                private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);\n" +
"                private static final int MAX_RETRY_ATTEMPTS = 3;\n" +
"                private static final long CACHE_EXPIRY_MS = 300000; // 5分钟\n" +
"\n" +
"                private final UserRepository userRepository;\n" +
"                private final CacheManager cacheManager;\n" +
"                private final ExecutorService executorService;\n" +
"                private final Map<String, UserCacheEntry> userCache;\n" +
"\n" +
"                public UserServiceImpl(UserRepository userRepository, CacheManager cacheManager) {\n" +
"                    this.userRepository = userRepository;\n" +
"                    this.cacheManager = cacheManager;\n" +
"                    this.executorService = Executors.newFixedThreadPool(10);\n" +
"                    this.userCache = new ConcurrentHashMap<>();\n" +
"                }\n" +
"\n" +
"                @Override\n" +
"                public User getUserById(String userId) {\n" +
"                    if (userId == null || userId.isEmpty()) {\n" +
"                        throw new IllegalArgumentException(\"User ID cannot be null or empty\");\n" +
"                    }\n" +
"\n" +
"                    // 检查缓存\n" +
"                    UserCacheEntry cacheEntry = userCache.get(userId);\n" +
"                    if (cacheEntry != null && !cacheEntry.isExpired()) {\n" +
"                        logger.debug(\"Cache hit for user: {}\", userId);\n" +
"                        return cacheEntry.getUser();\n" +
"                    }\n" +
"\n" +
"                    // 从数据库加载\n" +
"                    User user = loadUserWithRetry(userId);\n" +
"                    if (user != null) {\n" +
"                        userCache.put(userId, new UserCacheEntry(user));\n" +
"                    }\n" +
"\n" +
"                    return user;\n" +
"                }\n" +
"\n" +
"                @Override\n" +
"                public List<User> searchUsers(UserSearchCriteria criteria) {\n" +
"                    if (criteria == null) {\n" +
"                        throw new IllegalArgumentException(\"Search criteria cannot be null\");\n" +
"                    }\n" +
"\n" +
"                    try {\n" +
"                        List<User> users = userRepository.findByCriteria(criteria);\n" +
"                        return users.stream()\n" +
"                            .filter(user -> user.isActive())\n" +
"                            .sorted(Comparator.comparing(User::getCreatedAt).reversed())\n" +
"                            .collect(Collectors.toList());\n" +
"                    } catch (Exception e) {\n" +
"                        logger.error(\"Error searching users\", e);\n" +
"                        throw new ServiceException(\"Failed to search users\", e);\n" +
"                    }\n" +
"                }\n" +
"\n" +
"                @Override\n" +
"                public void updateUser(String userId, UserUpdateRequest request) {\n" +
"                    validateUpdateRequest(request);\n" +
"\n" +
"                    User existingUser = getUserById(userId);\n" +
"                    if (existingUser == null) {\n" +
"                        throw new NotFoundException(\"User not found: \" + userId);\n" +
"                    }\n" +
"\n" +
"                    // 更新用户信息\n" +
"                    existingUser.setEmail(request.getEmail());\n" +
"                    existingUser.setName(request.getName());\n" +
"                    existingUser.setUpdatedAt(new Date());\n" +
"\n" +
"                    // 保存到数据库\n" +
"                    userRepository.save(existingUser);\n" +
"\n" +
"                    // 清除缓存\n" +
"                    userCache.remove(userId);\n" +
"\n" +
"                    // 异步发送通知\n" +
"                    executorService.submit(() -> sendUpdateNotification(existingUser));\n" +
"                }\n" +
"\n" +
"                @Override\n" +
"                public CompletableFuture<List<User>> getUsersAsync(List<String> userIds) {\n" +
"                    return CompletableFuture.supplyAsync(() -> {\n" +
"                        return userIds.stream()\n" +
"                            .map(this::getUserById)\n" +
"                            .filter(Objects::nonNull)\n" +
"                            .collect(Collectors.toList());\n" +
"                    }, executorService);\n" +
"                }\n" +
"\n" +
"                private User loadUserWithRetry(String userId) {\n" +
"                    int attempts = 0;\n" +
"                    Exception lastException = null;\n" +
"\n" +
"                    while (attempts < MAX_RETRY_ATTEMPTS) {\n" +
"                        try {\n" +
"                            return userRepository.findById(userId);\n" +
"                        } catch (Exception e) {\n" +
"                            lastException = e;\n" +
"                            attempts++;\n" +
"                            logger.warn(\"Failed to load user (attempt {}/{}): {}\",\n" +
"                                attempts, MAX_RETRY_ATTEMPTS, userId);\n" +
"\n" +
"                            if (attempts < MAX_RETRY_ATTEMPTS) {\n" +
"                                try {\n" +
"                                    Thread.sleep(100 * attempts);\n" +
"                                } catch (InterruptedException ie) {\n" +
"                                    Thread.currentThread().interrupt();\n" +
"                                    break;\n" +
"                                }\n" +
"                            }\n" +
"                        }\n" +
"                    }\n" +
"\n" +
"                    logger.error(\"Failed to load user after {} attempts: {}\",\n" +
"                        MAX_RETRY_ATTEMPTS, userId, lastException);\n" +
"                    return null;\n" +
"                }\n" +
"\n" +
"                private void validateUpdateRequest(UserUpdateRequest request) {\n" +
"                    if (request == null) {\n" +
"                        throw new IllegalArgumentException(\"Update request cannot be null\");\n" +
"                    }\n" +
"                    if (request.getEmail() == null || !isValidEmail(request.getEmail())) {\n" +
"                        throw new IllegalArgumentException(\"Invalid email address\");\n" +
"                    }\n" +
"                }\n" +
"\n" +
"                private boolean isValidEmail(String email) {\n" +
"                    return email != null && email.matches(\"^[A-Za-z0-9+_.-]+@(.+)$\");\n" +
"                }\n" +
"\n" +
"                private void sendUpdateNotification(User user) {\n" +
"                    try {\n" +
"                        logger.info(\"Sending update notification for user: {}\", user.getId());\n" +
"                        // 实际的通知逻辑\n" +
"                    } catch (Exception e) {\n" +
"                        logger.error(\"Failed to send notification\", e);\n" +
"                    }\n" +
"                }\n" +
"\n" +
"                private static class UserCacheEntry {\n" +
"                    private final User user;\n" +
"                    private final long timestamp;\n" +
"\n" +
"                    public UserCacheEntry(User user) {\n" +
"                        this.user = user;\n" +
"                        this.timestamp = System.currentTimeMillis();\n" +
"                    }\n" +
"\n" +
"                    public User getUser() {\n" +
"                        return user;\n" +
"                    }\n" +
"\n" +
"                    public boolean isExpired() {\n" +
"                        return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;\n" +
"                    }\n" +
"                }\n" +
"\n" +
"                @Override\n" +
"                public void shutdown() {\n" +
"                    executorService.shutdown();\n" +
"                    try {\n" +
"                        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {\n" +
"                            executorService.shutdownNow();\n" +
"                        }\n" +
"                    } catch (InterruptedException e) {\n" +
"                        executorService.shutdownNow();\n" +
"                        Thread.currentThread().interrupt();\n" +
"                    }\n" +
"                }\n" +
"            }\n" +
"            \n";

        logger.info("System prompt长度: {} 字符, 估算tokens: {} (最小要求: 1024)",
            systemPrompt.length(), estimateTokens(systemPrompt));
        logger.info("Code context长度: {} 字符, 估算tokens: {} (最小要求: 1024)",
            codeContext.length(), estimateTokens(codeContext));

        // 第一次请求
        ChatCompletionRequest request1 = new ChatCompletionRequest();
        request1.setModel(MODEL_ID);
        request1.setBedrockEnableSystemCache(true);
        request1.setMessages(Arrays.asList(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(
                        ChatMessage.ContentPart.textWithCache(codeContext, true),
                        ChatMessage.ContentPart.text("\n\n检查这段代码的性能问题。")
                )
        ));

        logger.info("=== 组合缓存测试 - 第一次请求 ===");
        ChatCompletionResponse response1 = client.createChatCompletion(request1);
        logger.info("回答: {}", response1.getContent());
        printUsageStats(response1.getUsage());

        // 第二次请求 - system和code都命中缓存
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ChatCompletionRequest request2 = new ChatCompletionRequest();
        request2.setModel(MODEL_ID);
        request2.setBedrockEnableSystemCache(true);
        request2.setMessages(Arrays.asList(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(
                        ChatMessage.ContentPart.textWithCache(codeContext, true),
                        ChatMessage.ContentPart.text("\n\n检查这段代码的安全问题。")
                )
        ));

        logger.info("\n=== 组合缓存测试 - 第二次请求 ===");
        ChatCompletionResponse response2 = client.createChatCompletion(request2);
        logger.info("回答: {}", response2.getContent());
        printUsageStats(response2.getUsage());
    }

    /**
     * 估算文本的token数量 (粗略估计: 1 token ≈ 4个字符)
     */
    private int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / 4;
    }

    /**
     * 打印使用统计,突出显示缓存效果
     */
    private void printUsageStats(ChatCompletionResponse.Usage usage) {
        logger.info("Token使用统计:");
        logger.info("  输入tokens: {}", usage.getPromptTokens());
        logger.info("  输出tokens: {}", usage.getCompletionTokens());
        logger.info("  总计tokens: {}", usage.getTotalTokens());

        // 缓存统计
        boolean hasCache = false;

        if (usage.getCacheCreationInputTokens() != null && usage.getCacheCreationInputTokens() > 0) {
            logger.info("  ⚠️  缓存创建tokens: {} (首次创建,成本约为标准的125%)", usage.getCacheCreationInputTokens());
            hasCache = true;
        }

        if (usage.getCacheReadInputTokens() != null && usage.getCacheReadInputTokens() > 0) {
            logger.info("  ✅ 缓存读取tokens: {} (节省约90%成本!)", usage.getCacheReadInputTokens());
            double savings = usage.getCacheReadInputTokens() * 0.9;
            logger.info("  💰 估算节省: ~{} 标准输入tokens的成本 (约${} at $0.003/1K)",
                Math.round(savings),
                String.format("%.6f", savings * 0.003 / 1000));
            hasCache = true;
        }

        if (!hasCache) {
            logger.info("  ℹ️  未使用缓存 (可能是内容不足1024 tokens或首次请求)");
        }
    }

    /**
     * 测试4: 流式响应中的Prompt Caching
     * 验证缓存在流式场景下也能正常工作
     */
    @Test
    public void testStreamingWithCaching() throws InterruptedException {
        OpenAI client = createClient();
        logger.info("测试配置: 区域={}, 模型={}", REGION, MODEL_ID);

        // 准备长system prompt - 使用完整版本确保超过1024 tokens
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
"            11. 深度学习框架:\n" +
"                - TensorFlow和Keras的使用场景\n" +
"                - PyTorch的优势和特点\n" +
"                - JAX在研究中的应用\n" +
"                - 不同框架的性能对比\n" +
"                - 模型训练和优化技巧\n" +
"                - 分布式训练策略\n" +
"\n" +
"            12. 数据处理:\n" +
"                - 数据预处理的重要性\n" +
"                - 特征工程最佳实践\n" +
"                - 数据增强技术\n" +
"                - 处理不平衡数据集\n" +
"                - 数据标注质量控制\n" +
"                - 大规模数据集管理\n" +
"\n" +
"            在回答时,请始终保持专业、准确、友好的态度,帮助用户深入理解AI技术。\n" +
"            对于复杂问题,建议分步骤解答,确保用户能够充分理解每个环节。\n" +
"            \n";

        logger.info("System prompt长度: {} 字符, 估算tokens: {} (最小要求: 1024)",
            longSystemPrompt.length(), estimateTokens(longSystemPrompt));

        // 第一次流式请求 - 创建缓存
        logger.info("\n=== 第一次流式请求 (创建缓存) ===");

        ChatCompletionRequest request1 = new ChatCompletionRequest();
        request1.setModel(MODEL_ID);
        request1.setBedrockEnableSystemCache(true);
        request1.setMaxTokens(200);
        request1.setMessages(Arrays.asList(
            ChatMessage.system(longSystemPrompt),
            ChatMessage.user("什么是Transformer架构?")
        ));

        CountDownLatch latch1 = new CountDownLatch(1);
        StringBuilder response1 = new StringBuilder();
        ChatCompletionResponse.Usage[] usage1 = new ChatCompletionResponse.Usage[1];

        client.createChatCompletionStream(
            request1,
            chunk -> {
                String content = chunk.getContent();
                if (content != null) {
                    System.out.print(content);
                    response1.append(content);
                }

                // 合并usage信息 (message_start有input_tokens, message_delta有output_tokens)
                if (chunk.getUsage() != null) {
                    if (usage1[0] == null) {
                        usage1[0] = chunk.getUsage();
                    } else {
                        // 合并usage: 保留已有的非零值,更新新的值
                        ChatCompletionResponse.Usage existing = usage1[0];
                        ChatCompletionResponse.Usage newUsage = chunk.getUsage();

                        // 更新prompt tokens (来自message_start)
                        if (newUsage.getPromptTokens() > 0) {
                            existing.setPromptTokens(newUsage.getPromptTokens());
                        }

                        // 更新completion tokens (来自message_delta)
                        if (newUsage.getCompletionTokens() > 0) {
                            existing.setCompletionTokens(newUsage.getCompletionTokens());
                        }

                        // 更新cache信息 (来自message_start)
                        if (newUsage.getCacheReadInputTokens() != null && newUsage.getCacheReadInputTokens() > 0) {
                            existing.setCacheReadInputTokens(newUsage.getCacheReadInputTokens());
                        }
                        if (newUsage.getCacheCreationInputTokens() != null && newUsage.getCacheCreationInputTokens() > 0) {
                            existing.setCacheCreationInputTokens(newUsage.getCacheCreationInputTokens());
                        }

                        // 更新total
                        existing.setTotalTokens(existing.getPromptTokens() + existing.getCompletionTokens());
                    }
                }
            },
            () -> {
                System.out.println("\n");
                logger.info("第一次流式响应完成,总字符数: {}", response1.length());
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

        // 等待一会儿
        Thread.sleep(1000);

        // 第二次流式请求 - 应该命中缓存
        logger.info("\n=== 第二次流式请求 (应该命中缓存) ===");

        ChatCompletionRequest request2 = new ChatCompletionRequest();
        request2.setModel(MODEL_ID);
        request2.setBedrockEnableSystemCache(true);
        request2.setMaxTokens(200);
        request2.setMessages(Arrays.asList(
            ChatMessage.system(longSystemPrompt), // 相同的system消息
            ChatMessage.user("解释一下注意力机制") // 不同的问题
        ));

        CountDownLatch latch2 = new CountDownLatch(1);
        StringBuilder response2 = new StringBuilder();
        ChatCompletionResponse.Usage[] usage2 = new ChatCompletionResponse.Usage[1];

        client.createChatCompletionStream(
            request2,
            chunk -> {
                String content = chunk.getContent();
                if (content != null) {
                    System.out.print(content);
                    response2.append(content);
                }

                // 合并usage信息 (message_start有input_tokens, message_delta有output_tokens)
                if (chunk.getUsage() != null) {
                    if (usage2[0] == null) {
                        usage2[0] = chunk.getUsage();
                    } else {
                        // 合并usage: 保留已有的非零值,更新新的值
                        ChatCompletionResponse.Usage existing = usage2[0];
                        ChatCompletionResponse.Usage newUsage = chunk.getUsage();

                        if (newUsage.getPromptTokens() > 0) {
                            existing.setPromptTokens(newUsage.getPromptTokens());
                        }
                        if (newUsage.getCompletionTokens() > 0) {
                            existing.setCompletionTokens(newUsage.getCompletionTokens());
                        }
                        if (newUsage.getCacheReadInputTokens() != null && newUsage.getCacheReadInputTokens() > 0) {
                            existing.setCacheReadInputTokens(newUsage.getCacheReadInputTokens());
                        }
                        if (newUsage.getCacheCreationInputTokens() != null && newUsage.getCacheCreationInputTokens() > 0) {
                            existing.setCacheCreationInputTokens(newUsage.getCacheCreationInputTokens());
                        }
                        existing.setTotalTokens(existing.getPromptTokens() + existing.getCompletionTokens());
                    }
                }
            },
            () -> {
                System.out.println("\n");
                logger.info("第二次流式响应完成,总字符数: {}", response2.length());
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

        // 验证缓存效果
        logger.info("\n=== 缓存效果对比 ===");
        if (usage1[0] != null && usage2[0] != null) {
            logger.info("第1次请求 - 输入tokens: {}, 缓存创建: {}",
                usage1[0].getPromptTokens(),
                usage1[0].getCacheCreationInputTokens());
            logger.info("第2次请求 - 输入tokens: {}, 缓存读取: {}",
                usage2[0].getPromptTokens(),
                usage2[0].getCacheReadInputTokens());

            if (usage2[0].getCacheReadInputTokens() != null && usage2[0].getCacheReadInputTokens() > 0) {
                logger.info("✅ 流式响应中缓存命中成功!");
            } else {
                logger.warn("⚠️  流式响应中未命中缓存");
            }
        }
    }

    /**
     * 测试5: 多轮对话中的缓存效果
     * 测试在多轮对话场景下,历史消息的缓存效果
     */
    @Test
    public void testMultiTurnConversationCaching() throws InterruptedException {
        OpenAI client = createClient();
        logger.info("测试配置: 区域={}, 模型={}", REGION, MODEL_ID);

        // 准备长system prompt - 确保超过1024 tokens
        String systemPrompt = "你是一个专业的编程助手,擅长Java、Python、Go、Rust等多种编程语言开发。\n" +
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
"            9. 文档规范:\n" +
"               - 编写清晰的函数文档\n" +
"               - 提供使用示例\n" +
"               - 说明参数和返回值\n" +
"               - 注明可能的异常情况\n" +
"\n" +
"            10. 版本控制:\n" +
"                - 遵循Git最佳实践\n" +
"                - 编写有意义的提交信息\n" +
"                - 合理组织代码变更\n" +
"                - 考虑代码审查流程\n" +
"\n" +
"            请始终以专业、耐心、友好的态度回答问题,帮助开发者提升技术能力。\n" +
"            \n";

        // 构建多轮对话历史 - 添加更多详细内容
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(ChatMessage.system(systemPrompt));

        conversation.add(ChatMessage.user("如何实现一个线程安全的单例模式?请给出Java实现。"));
        conversation.add(ChatMessage.assistant(
            "以下是使用双重检查锁定(DCL)实现的线程安全单例模式:\n\n" +
            "```java\n" +
            "public class Singleton {\n" +
            "    // 使用volatile确保可见性和禁止指令重排序\n" +
            "    private static volatile Singleton instance;\n" +
            "    \n" +
            "    // 私有构造函数防止外部实例化\n" +
            "    private Singleton() {\n" +
            "        if (instance != null) {\n" +
            "            throw new IllegalStateException(\"Already initialized\");\n" +
            "        }\n" +
            "    }\n" +
            "    \n" +
            "    public static Singleton getInstance() {\n" +
            "        // 第一次检查,避免不必要的同步\n" +
            "        if (instance == null) {\n" +
            "            synchronized (Singleton.class) {\n" +
            "                // 第二次检查,确保只创建一个实例\n" +
            "                if (instance == null) {\n" +
            "                    instance = new Singleton();\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "        return instance;\n" +
            "    }\n" +
            "}\n" +
            "```\n\n" +
            "关键点:\n" +
            "1. volatile保证多线程环境下的可见性\n" +
            "2. 双重检查减少同步开销\n" +
            "3. 私有构造函数防止反射攻击可以加强\n"
        ));

        conversation.add(ChatMessage.user("为什么volatile关键字在这里很重要?如果去掉会有什么问题?"));
        conversation.add(ChatMessage.assistant(
            "volatile关键字在DCL单例中至关重要,原因如下:\n\n" +
            "1. **防止指令重排序**:\n" +
            "   对象创建实际包含三个步骤:\n" +
            "   - 分配内存空间\n" +
            "   - 初始化对象\n" +
            "   - 将引用指向内存地址\n" +
            "   \n" +
            "   没有volatile时,JVM可能重排序为:分配内存→指向地址→初始化对象\n" +
            "   这会导致其他线程看到未初始化的对象!\n\n" +
            "2. **确保可见性**:\n" +
            "   volatile保证一个线程对instance的修改对其他线程立即可见\n" +
            "   避免线程从本地缓存读取到null或未完全初始化的对象\n\n" +
            "3. **happens-before保证**:\n" +
            "   volatile写happens-before任何后续的volatile读\n" +
            "   确保对象完全初始化后才能被其他线程访问\n\n" +
            "如果去掉volatile,在高并发场景下可能出现NPE或访问到半初始化对象的严重问题。"
        ));

        // 计算对话历史的tokens
        StringBuilder historyText = new StringBuilder();
        for (ChatMessage msg : conversation) {
            if (msg.getContent() != null) {
                historyText.append(msg.getContent()).append("\n");
            }
        }

        logger.info("对话历史长度: {} 条消息, 估算tokens: {}",
            conversation.size(),
            estimateTokens(historyText.toString()));

        // 第一次请求 - 使用ContentPart缓存对话历史
        logger.info("\n=== 第一次请求 (缓存对话历史) ===");

        StringBuilder contextBuilder = new StringBuilder();
        for (ChatMessage msg : conversation) {
            if (msg.getRole() != null) {
                contextBuilder.append(msg.getRole()).append(": ");
            }
            if (msg.getContent() != null) {
                contextBuilder.append(msg.getContent());
            }
            contextBuilder.append("\n\n");
        }
        String conversationContext = contextBuilder.toString();

        ChatCompletionRequest request1 = new ChatCompletionRequest();
        request1.setModel(MODEL_ID);
        request1.setMaxTokens(150);
        request1.setMessages(Arrays.asList(
            ChatMessage.user(
                ChatMessage.ContentPart.textWithCache(conversationContext, true),
                ChatMessage.ContentPart.text("\n\n新问题: volatile关键字的作用是什么?")
            )
        ));

        CountDownLatch latch1 = new CountDownLatch(1);
        ChatCompletionResponse.Usage[] usage1 = new ChatCompletionResponse.Usage[1];

        client.createChatCompletionStream(
            request1,
            chunk -> {
                String content = chunk.getContent();
                if (content != null) {
                    System.out.print(content);
                }
                // 合并usage信息
                if (chunk.getUsage() != null) {
                    if (usage1[0] == null) {
                        usage1[0] = chunk.getUsage();
                    } else {
                        ChatCompletionResponse.Usage existing = usage1[0];
                        ChatCompletionResponse.Usage newUsage = chunk.getUsage();
                        if (newUsage.getPromptTokens() > 0) existing.setPromptTokens(newUsage.getPromptTokens());
                        if (newUsage.getCompletionTokens() > 0) existing.setCompletionTokens(newUsage.getCompletionTokens());
                        if (newUsage.getCacheReadInputTokens() != null && newUsage.getCacheReadInputTokens() > 0) {
                            existing.setCacheReadInputTokens(newUsage.getCacheReadInputTokens());
                        }
                        if (newUsage.getCacheCreationInputTokens() != null && newUsage.getCacheCreationInputTokens() > 0) {
                            existing.setCacheCreationInputTokens(newUsage.getCacheCreationInputTokens());
                        }
                        existing.setTotalTokens(existing.getPromptTokens() + existing.getCompletionTokens());
                    }
                }
            },
            () -> {
                System.out.println("\n");
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

        // 第二次请求 - 相同历史,不同问题
        logger.info("\n=== 第二次请求 (应该命中缓存) ===");

        ChatCompletionRequest request2 = new ChatCompletionRequest();
        request2.setModel(MODEL_ID);
        request2.setMaxTokens(150);
        request2.setMessages(Arrays.asList(
            ChatMessage.user(
                ChatMessage.ContentPart.textWithCache(conversationContext, true), // 相同的历史
                ChatMessage.ContentPart.text("\n\n新问题: synchronized和Lock的区别?")
            )
        ));

        CountDownLatch latch2 = new CountDownLatch(1);
        ChatCompletionResponse.Usage[] usage2 = new ChatCompletionResponse.Usage[1];

        client.createChatCompletionStream(
            request2,
            chunk -> {
                String content = chunk.getContent();
                if (content != null) {
                    System.out.print(content);
                }
                // 合并usage信息
                if (chunk.getUsage() != null) {
                    if (usage2[0] == null) {
                        usage2[0] = chunk.getUsage();
                    } else {
                        ChatCompletionResponse.Usage existing = usage2[0];
                        ChatCompletionResponse.Usage newUsage = chunk.getUsage();
                        if (newUsage.getPromptTokens() > 0) existing.setPromptTokens(newUsage.getPromptTokens());
                        if (newUsage.getCompletionTokens() > 0) existing.setCompletionTokens(newUsage.getCompletionTokens());
                        if (newUsage.getCacheReadInputTokens() != null && newUsage.getCacheReadInputTokens() > 0) {
                            existing.setCacheReadInputTokens(newUsage.getCacheReadInputTokens());
                        }
                        if (newUsage.getCacheCreationInputTokens() != null && newUsage.getCacheCreationInputTokens() > 0) {
                            existing.setCacheCreationInputTokens(newUsage.getCacheCreationInputTokens());
                        }
                        existing.setTotalTokens(existing.getPromptTokens() + existing.getCompletionTokens());
                    }
                }
            },
            () -> {
                System.out.println("\n");
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

        // 总结
        logger.info("\n=== 多轮对话缓存效果 ===");
        if (usage1[0] != null && usage2[0] != null) {
            logger.info("第1次 - 缓存创建tokens: {}", usage1[0].getCacheCreationInputTokens());
            logger.info("第2次 - 缓存读取tokens: {}", usage2[0].getCacheReadInputTokens());

            if (usage2[0].getCacheReadInputTokens() != null && usage2[0].getCacheReadInputTokens() > 0) {
                logger.info("✅ 多轮对话缓存工作正常!");
            }
        }
    }
}
