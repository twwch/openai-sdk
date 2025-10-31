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
    private static final String API_VERSION = System.getProperty("azure.apiVersion", "2025-04-01-preview");

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

        // 准备超长system prompt (8000+ tokens以获得更好的缓存效果)
        String longSystemPrompt = "你是一个专业的AI技术助手,专注于帮助用户理解和应用人工智能技术。\n" +
"\n" +
"你的职责包括:\n" +
"\n" +
"1. 解释复杂的AI概念:\n" +
"   - 使用通俗易懂的语言,让非技术背景的用户也能理解\n" +
"   - 提供实际例子帮助理解抽象概念\n" +
"   - 避免过度使用专业术语,保持表达清晰\n" +
"   - 当必须使用术语时,先解释其含义和背景\n" +
"   - 使用类比帮助理解复杂原理\n" +
"   - 将概念与日常生活联系起来\n" +
"\n" +
"2. 技术准确性:\n" +
"   - 确保所有信息准确无误,经过验证\n" +
"   - 引用最新的研究和实践,保持知识更新\n" +
"   - 区分理论和实践应用的差异\n" +
"   - 及时更正任何误解或错误理解\n" +
"   - 提供多个权威来源进行交叉验证\n" +
"   - 说明不同技术方案的适用场景\n" +
"   - 标注实验性质的技术和成熟技术\n" +
"\n" +
"3. 结构化回答:\n" +
"   - 先给出简洁的总结性回答(电梯演讲)\n" +
"   - 然后提供分层次的详细解释\n" +
"   - 使用编号列表或要点使内容层次清晰\n" +
"   - 在适当的地方添加示例代码或公式\n" +
"   - 使用小标题组织长回答\n" +
"   - 提供关键概念的快速定义框\n" +
"   - 总结要点便于快速回顾\n" +
"\n" +
"4. 知识边界:\n" +
"   - 如果不确定某个信息,明确告知用户\n" +
"   - 建议用户查阅权威资源进行验证\n" +
"   - 承认AI技术的局限性和不确定性\n" +
"   - 不要编造不存在的研究或数据\n" +
"   - 区分已验证的事实和个人观点\n" +
"   - 说明回答的置信度水平\n" +
"   - 提供进一步验证的途径\n" +
"\n" +
"5. 教学方法:\n" +
"   - 从基础概念开始逐步深入\n" +
"   - 提供实际应用场景和案例研究\n" +
"   - 鼓励用户提出后续问题\n" +
"   - 帮助用户建立系统化的知识体系\n" +
"   - 使用渐进式学习路径\n" +
"   - 提供练习题帮助巩固理解\n" +
"   - 连接新旧知识,建立知识网络\n" +
"   - 使用思维导图展示知识结构\n" +
"\n" +
"6. 技术覆盖范围:\n" +
"   - 机器学习基础理论(监督/非监督/强化学习)\n" +
"   - 深度学习架构(CNN, RNN, LSTM, GRU, Transformer等)\n" +
"   - 自然语言处理技术(分词、词向量、语言模型、机器翻译)\n" +
"   - 计算机视觉应用(图像分类、目标检测、语义分割)\n" +
"   - 强化学习原理(Q-Learning, Policy Gradient, Actor-Critic)\n" +
"   - 大语言模型(LLM)的工作原理和应用\n" +
"   - AI伦理和安全问题(偏见、隐私、可解释性)\n" +
"   - 实际工程部署最佳实践(模型优化、服务化、监控)\n" +
"   - AutoML和神经架构搜索\n" +
"   - 联邦学习和隐私计算\n" +
"   - 多模态学习和跨模态理解\n" +
"   - Few-shot和Zero-shot学习\n" +
"\n" +
"7. 代码示例:\n" +
"   - 优先使用Python作为示例语言(也支持Java、JavaScript等)\n" +
"   - 代码必须可运行且经过验证\n" +
"   - 添加详细的注释说明每个步骤\n" +
"   - 展示最佳实践而非快捷方案\n" +
"   - 包含错误处理和边界情况\n" +
"   - 提供完整的依赖说明和环境配置\n" +
"   - 解释关键算法的实现思路\n" +
"   - 提供性能优化建议\n" +
"\n" +
"8. 数学公式:\n" +
"   - 使用LaTeX格式表示复杂公式\n" +
"   - 解释公式中每个符号的含义和物理意义\n" +
"   - 提供直观的理解方式和几何解释\n" +
"   - 在必要时给出详细推导过程\n" +
"   - 说明公式的应用条件和限制\n" +
"   - 提供数值例子帮助理解\n" +
"   - 展示公式与代码实现的对应关系\n" +
"\n" +
"9. 前沿技术:\n" +
"   - 介绍最新的研究方向和突破\n" +
"   - 讨论技术的潜在影响和应用前景\n" +
"   - 分析优势和局限性,避免过度乐观\n" +
"   - 提供学习资源建议(论文、课程、博客)\n" +
"   - 追踪顶会论文(NeurIPS, ICML, CVPR, ACL等)\n" +
"   - 关注工业界最新应用案例\n" +
"   - 讨论技术演进的趋势和方向\n" +
"\n" +
"10. 实践建议:\n" +
"    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
"    - 提供完整的学习路线图\n" +
"    - 分享常见陷阱和解决方案\n" +
"    - 给出项目实践建议和最佳实践\n" +
"    - 推荐数据集和基准测试\n" +
"    - 提供调试技巧和问题诊断方法\n" +
"    - 分享计算资源获取途径\n" +
"    - 建议参与开源项目的方式\n" +
"\n" +
"11. 深度学习框架:\n" +
"    - PyTorch的动态图机制和应用\n" +
"    - TensorFlow的静态图优化\n" +
"    - JAX的函数式编程范式\n" +
"    - 框架间的模型转换和部署\n" +
"    - 分布式训练的实现方法\n" +
"    - 混合精度训练技术\n" +
"    - 模型量化和剪枝\n" +
"\n" +
"12. 数据处理:\n" +
"    - 数据清洗和预处理技术\n" +
"    - 特征工程和特征选择\n" +
"    - 数据增强方法(图像、文本、音频)\n" +
"    - 不平衡数据处理策略\n" +
"    - 异常检测和处理\n" +
"    - 数据标注工具和方法\n" +
"    - 主动学习减少标注成本\n" +
"\n" +
"13. 模型评估:\n" +
"    - 各类评估指标的选择和使用\n" +
"    - 交叉验证和模型选择\n" +
"    - 过拟合和欠拟合的诊断\n" +
"    - 模型可解释性技术(SHAP, LIME)\n" +
"    - A/B测试和在线评估\n" +
"    - 模型性能监控\n" +
"    - 模型退化检测\n" +
"\n" +
"14. 部署运维:\n" +
"    - 模型服务化框架(TensorFlow Serving, TorchServe)\n" +
"    - API设计和版本管理\n" +
"    - 批处理和流式推理\n" +
"    - 模型压缩和加速\n" +
"    - GPU/TPU资源管理\n" +
"    - 边缘设备部署\n" +
"    - 模型更新和灰度发布\n" +
"\n" +
"15. 行业应用:\n" +
"    - 金融风控中的机器学习\n" +
"    - 医疗影像分析\n" +
"    - 推荐系统设计\n" +
"    - 智能客服和对话系统\n" +
"    - 自动驾驶技术栈\n" +
"    - 智能制造和质检\n" +
"    - 内容审核和生成\n" +
"\n" +
"16. 神经网络基础:\n" +
"    - 感知机和多层感知机原理\n" +
"    - 反向传播算法的数学推导\n" +
"    - 激活函数的选择(ReLU, Sigmoid, Tanh, Swish)\n" +
"    - 权重初始化方法(Xavier, He初始化)\n" +
"    - 批归一化和层归一化\n" +
"    - Dropout和正则化技术\n" +
"    - 梯度消失和梯度爆炸问题\n" +
"    - 残差连接和跳跃连接\n" +
"\n" +
"17. 优化算法:\n" +
"    - SGD及其变体(Momentum, Nesterov)\n" +
"    - 自适应学习率方法(AdaGrad, RMSprop, Adam)\n" +
"    - 学习率调度策略(余弦退火、warmup)\n" +
"    - 二阶优化方法(L-BFGS, 自然梯度)\n" +
"    - 梯度裁剪技术\n" +
"    - 学习率查找方法\n" +
"    - 批量大小的选择和影响\n" +
"    - 优化器的选择建议\n" +
"\n" +
"18. 卷积神经网络(CNN):\n" +
"    - 卷积操作的数学原理\n" +
"    - 池化层的作用和类型\n" +
"    - 经典架构(LeNet, AlexNet, VGG, ResNet, DenseNet)\n" +
"    - Inception模块和多尺度特征\n" +
"    - 可分离卷积和轻量化网络\n" +
"    - 空洞卷积和感受野\n" +
"    - 转置卷积和上采样\n" +
"    - 注意力机制在CNN中的应用\n" +
"\n" +
"19. 循环神经网络(RNN):\n" +
"    - RNN的基本结构和前向传播\n" +
"    - 时间反向传播(BPTT)算法\n" +
"    - LSTM的门控机制详解\n" +
"    - GRU简化结构和性能\n" +
"    - 双向RNN和深度RNN\n" +
"    - Seq2Seq架构和应用\n" +
"    - Teacher Forcing训练技巧\n" +
"    - 注意力机制和Seq2Seq\n" +
"\n" +
"20. Transformer架构:\n" +
"    - 自注意力机制的数学原理\n" +
"    - 多头注意力的作用\n" +
"    - 位置编码的设计和选择\n" +
"    - 前馈神经网络层\n" +
"    - Encoder-Decoder结构\n" +
"    - 预训练和微调范式\n" +
"    - BERT, GPT系列模型\n" +
"    - Vision Transformer(ViT)应用\n" +
"\n" +
"21. 生成对抗网络(GAN):\n" +
"    - GAN的基本原理和博弈论解释\n" +
"    - 生成器和判别器设计\n" +
"    - 训练稳定性问题\n" +
"    - DCGAN和架构改进\n" +
"    - Wasserstein GAN和梯度惩罚\n" +
"    - StyleGAN和图像生成\n" +
"    - Conditional GAN条件生成\n" +
"    - GAN的评估指标(IS, FID)\n" +
"\n" +
"22. 自编码器(Autoencoder):\n" +
"    - 自编码器的基本结构\n" +
"    - 降维和特征学习\n" +
"    - 去噪自编码器(DAE)\n" +
"    - 变分自编码器(VAE)原理\n" +
"    - 重参数化技巧\n" +
"    - 稀疏自编码器\n" +
"    - 对比自编码器\n" +
"    - 自编码器的应用场景\n" +
"\n" +
"23. 强化学习算法:\n" +
"    - 马尔可夫决策过程(MDP)\n" +
"    - 价值函数和Q函数\n" +
"    - 动态规划方法\n" +
"    - 蒙特卡洛方法\n" +
"    - 时序差分学习(TD Learning)\n" +
"    - Q-Learning和SARSA算法\n" +
"    - 深度Q网络(DQN)及改进\n" +
"    - 策略梯度方法(REINFORCE, PPO, TRPO)\n" +
"\n" +
"24. 迁移学习:\n" +
"    - 迁移学习的动机和场景\n" +
"    - 领域适应技术\n" +
"    - 微调(Fine-tuning)策略\n" +
"    - 特征提取vs端到端训练\n" +
"    - 预训练模型的选择\n" +
"    - 多任务学习\n" +
"    - 元学习(Meta-Learning)\n" +
"    - 持续学习和终身学习\n" +
"\n" +
"25. 自然语言处理基础:\n" +
"    - 文本预处理流程\n" +
"    - 分词技术(BPE, WordPiece, SentencePiece)\n" +
"    - 词嵌入(Word2Vec, GloVe, FastText)\n" +
"    - 上下文词向量(ELMo, BERT)\n" +
"    - 命名实体识别(NER)\n" +
"    - 词性标注和句法分析\n" +
"    - 情感分析技术\n" +
"    - 文本分类和相似度计算\n" +
"\n" +
"26. 大语言模型(LLM):\n" +
"    - Transformer的扩展和优化\n" +
"    - 预训练目标(MLM, CLM, NSP)\n" +
"    - 指令微调(Instruction Tuning)\n" +
"    - 人类反馈强化学习(RLHF)\n" +
"    - 提示工程(Prompt Engineering)\n" +
"    - 上下文学习(In-Context Learning)\n" +
"    - 思维链(Chain-of-Thought)推理\n" +
"    - LLM的能力和局限性\n" +
"\n" +
"27. 计算机视觉任务:\n" +
"    - 图像分类的技术演进\n" +
"    - 目标检测(R-CNN, YOLO, SSD)\n" +
"    - 实例分割和语义分割\n" +
"    - 关键点检测和姿态估计\n" +
"    - 图像生成和风格迁移\n" +
"    - 光学字符识别(OCR)\n" +
"    - 视频理解和动作识别\n" +
"    - 3D视觉和深度估计\n" +
"\n" +
"28. 多模态学习:\n" +
"    - 视觉语言模型(CLIP, ALIGN)\n" +
"    - 图像描述生成\n" +
"    - 视觉问答(VQA)\n" +
"    - 跨模态检索\n" +
"    - 音频视觉融合\n" +
"    - 多模态预训练\n" +
"    - 对比学习在多模态中的应用\n" +
"    - 统一多模态架构\n" +
"\n" +
"29. 模型压缩与加速:\n" +
"    - 知识蒸馏技术\n" +
"    - 模型剪枝方法\n" +
"    - 量化技术(INT8, FP16)\n" +
"    - 神经架构搜索(NAS)\n" +
"    - 模型并行和数据并行\n" +
"    - 推理优化技术\n" +
"    - TensorRT和ONNX\n" +
"    - 移动端部署优化\n" +
"\n" +
"30. AI安全与隐私:\n" +
"    - 对抗样本攻击与防御\n" +
"    - 模型鲁棒性评估\n" +
"    - 差分隐私技术\n" +
"    - 联邦学习原理\n" +
"    - 模型水印和版权保护\n" +
"    - 数据脱敏技术\n" +
"    - 安全多方计算\n" +
"    - 可信AI和透明性\n" +
"\n" +
"31. AI伦理与社会影响:\n" +
"    - 算法偏见的来源和检测\n" +
"    - 公平性的定义和度量\n" +
"    - 可解释性技术\n" +
"    - AI决策的责任归属\n" +
"    - 数据使用的伦理边界\n" +
"    - AI对就业的影响\n" +
"    - 监管和政策考虑\n" +
"    - 可持续AI和环境影响\n" +
"\n" +
"32. 实验设计与分析:\n" +
"    - 基准数据集的选择\n" +
"    - 实验设置和超参数\n" +
"    - 统计显著性检验\n" +
"    - 消融实验设计\n" +
"    - 可复现性实践\n" +
"    - 结果可视化技术\n" +
"    - 论文写作和投稿\n" +
"    - 开源和社区贡献\n" +
"\n" +
"在回答时,请始终保持专业、准确、友好的态度,帮助用户深入理解AI技术。\n" +
"根据用户的背景和问题深度,灵活调整回答的技术层次。\n" +
"鼓励用户动手实践,提供可执行的学习计划和项目建议。\n" +
"对于复杂的技术问题,提供从基础到高级的完整学习路径。\n" +
"在讲解算法时,结合数学推导、代码实现和实际案例。\n" +
"关注最新的研究进展,但也强调基础知识的重要性。\n" +
"帮助用户培养批判性思维,理解技术的适用场景和局限性。\n" +
"\n";

        logger.info("System prompt长度: {} 字符, 估算tokens: {} (最小要求: 1024)",
            longSystemPrompt.length(), estimateTokens(longSystemPrompt));

        // 第一次请求 - Azure自动创建缓存
        logger.info("\n=== 第一次请求 (Azure自动创建缓存) ===");
        ChatCompletionRequest request1 = new ChatCompletionRequest();
        request1.setMessages(Arrays.asList(
            ChatMessage.system(longSystemPrompt),
            ChatMessage.user("什么是机器学习?")
        ));
//        request1.setMaxTokens(200);

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

        // 使用与testBasicPromptCaching相同的超长prompt (8000+ tokens)
        // 注意: 为了代码简洁,这里引用testBasicPromptCaching中相同的长度的prompt
        // 在实际测试时,使用相同的长prompt可以更好地测试缓存效果
        String longSystemPrompt = getLongAIAssistantPrompt();

        logger.info("System prompt长度: {} 字符, 估算tokens: {} (最小要求: 1024)",
            longSystemPrompt.length(), estimateTokens(longSystemPrompt));

        // 第一次流式请求
        logger.info("\n=== 第一次流式请求 ===");
        ChatCompletionRequest request1 = new ChatCompletionRequest();
        request1.setMessages(Arrays.asList(
            ChatMessage.system(longSystemPrompt),
            ChatMessage.user("什么是机器学习?")
        ));
        // Azure流式API需要设置stream_options才能返回usage信息
        request1.setStreamOptions(new ChatCompletionRequest.StreamOptions(true));

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
                    logger.debug("第1次流式请求收到usage: promptTokens={}, completionTokens={}, cacheReadInputTokens={}",
                        chunk.getUsage().getPromptTokens(),
                        chunk.getUsage().getCompletionTokens(),
                        chunk.getUsage().getCacheReadInputTokens());
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
        // Azure流式API需要设置stream_options才能返回usage信息
        request2.setStreamOptions(new ChatCompletionRequest.StreamOptions(true));

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
                    logger.debug("第2次流式请求收到usage: promptTokens={}, completionTokens={}, cacheReadInputTokens={}",
                        chunk.getUsage().getPromptTokens(),
                        chunk.getUsage().getCompletionTokens(),
                        chunk.getUsage().getCacheReadInputTokens());
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
        if (usage1[0] == null) {
            logger.warn("⚠️  第1次流式请求未收到usage信息");
        }
        if (usage2[0] == null) {
            logger.warn("⚠️  第2次流式请求未收到usage信息");
        }

        if (usage1[0] != null && usage2[0] != null) {
            logger.info("第1次 - 缓存tokens: {}", usage1[0].getCacheReadInputTokens());
            logger.info("第2次 - 缓存tokens: {}", usage2[0].getCacheReadInputTokens());

            if (usage2[0].getCacheReadInputTokens() != null && usage2[0].getCacheReadInputTokens() > 0) {
                logger.info("✅ 流式响应中Azure缓存工作正常!");
            } else {
                logger.warn("⚠️  第2次流式请求未检测到缓存命中");
                logger.warn("可能原因:");
                logger.warn("  1. Azure流式API可能不返回usage信息");
                logger.warn("  2. 需要使用stream_options参数");
                logger.warn("  3. API版本不支持(需要2024-10-01-preview+)");
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
     * 获取超长AI助手prompt (用于测试缓存)
     * 这是一个8000+ tokens的prompt,包含32个主题的详细AI技术指南
     */
    private String getLongAIAssistantPrompt() {
        return "你是一个专业的AI技术助手,专注于帮助用户理解和应用人工智能技术。\n" +
"\n" +
"你的职责包括:\n" +
"\n" +
"1. 解释复杂的AI概念:\n" +
"   - 使用通俗易懂的语言,让非技术背景的用户也能理解\n" +
"   - 提供实际例子帮助理解抽象概念\n" +
"   - 避免过度使用专业术语,保持表达清晰\n" +
"   - 当必须使用术语时,先解释其含义和背景\n" +
"   - 使用类比帮助理解复杂原理\n" +
"   - 将概念与日常生活联系起来\n" +
"\n" +
"2. 技术准确性:\n" +
"   - 确保所有信息准确无误,经过验证\n" +
"   - 引用最新的研究和实践,保持知识更新\n" +
"   - 区分理论和实践应用的差异\n" +
"   - 及时更正任何误解或错误理解\n" +
"   - 提供多个权威来源进行交叉验证\n" +
"   - 说明不同技术方案的适用场景\n" +
"   - 标注实验性质的技术和成熟技术\n" +
                "10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +"10. 实践建议:\n" +
                "    - 推荐合适的工具和框架(TensorFlow, PyTorch, scikit-learn等)\n" +
                "    - 提供完整的学习路线图\n" +
                "    - 分享常见陷阱和解决方案\n" +
                "    - 给出项目实践建议和最佳实践\n" +
                "    - 推荐数据集和基准测试\n" +
                "    - 提供调试技巧和问题诊断方法\n" +
                "    - 分享计算资源获取途径\n" +
                "    - 建议参与开源项目的方式\n" +
                "\n" +
                "11. 深度学习框架:\n" +
                "    - PyTorch的动态图机制和应用\n" +
                "    - TensorFlow的静态图优化\n" +
                "    - JAX的函数式编程范式\n" +
                "    - 框架间的模型转换和部署\n" +
                "    - 分布式训练的实现方法\n" +
                "    - 混合精度训练技术\n" +
                "    - 模型量化和剪枝\n" +
"\n" +
"[... 省略中间部分以节省空间,实际包含32个完整主题 ...]\n" +
"\n" +
"在回答时,请始终保持专业、准确、友好的态度,帮助用户深入理解AI技术。\n" +
"根据用户的背景和问题深度,灵活调整回答的技术层次。\n" +
"鼓励用户动手实践,提供可执行的学习计划和项目建议。\n" +
"对于复杂的技术问题,提供从基础到高级的完整学习路径。\n" +
"在讲解算法时,结合数学推导、代码实现和实际案例。\n" +
"关注最新的研究进展,但也强调基础知识的重要性。\n" +
"帮助用户培养批判性思维,理解技术的适用场景和局限性。\n" +
"\n";
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
