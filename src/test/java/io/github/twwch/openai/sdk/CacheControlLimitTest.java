package io.github.twwch.openai.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 测试 AWS Bedrock cache_control 数量限制问题的自动处理
 *
 * 问题描述:
 * AWS Bedrock 的 Claude 模型最多只允许 4 个 cache_control 块
 * 如果超过这个限制会报错:
 * ValidationException: A maximum of 4 blocks with cache_control may be provided. Found 5.
 *
 * 解决方案:
 * ClaudeModelAdapter 现在会自动检测并限制 cache_control 块的数量
 * 如果超过限制，会从后往前移除多余的 cache_control 块
 *
 * 运行方式:
 * mvn test -Dtest=CacheControlLimitTest -Dbedrock.region=us-west-2
 */
public class CacheControlLimitTest {
    private static final Logger logger = LoggerFactory.getLogger(CacheControlLimitTest.class);

    // 测试配置
    private static final String REGION = System.getProperty("bedrock.region", "us-west-2");
    private static final String MODEL_ID = System.getProperty("bedrock.modelId", "us.anthropic.claude-3-7-sonnet-20250219-v1:0");
    private static final String ACCESS_KEY_ID = System.getProperty("bedrock.accessKeyId", System.getenv("AWS_ACCESS_KEY_ID"));
    private static final String SECRET_ACCESS_KEY = System.getProperty("bedrock.secretAccessKey", System.getenv("AWS_SECRET_ACCESS_KEY"));

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建OpenAI客户端
     */
    private OpenAI createClient() {
        if (ACCESS_KEY_ID != null && SECRET_ACCESS_KEY != null) {
            logger.info("使用显式提供的AWS凭证");
            return OpenAI.bedrock(REGION, ACCESS_KEY_ID, SECRET_ACCESS_KEY, MODEL_ID);
        } else {
            logger.info("使用默认AWS凭证链");
            return OpenAI.bedrock(REGION, MODEL_ID);
        }
    }

    /**
     * 测试: 读取 message.json 并发送请求
     * ClaudeModelAdapter 会自动限制 cache_control 数量
     */
    @Test
    public void testSendRequestWithMessageJson() throws Exception {
        File messageFile = new File("message.json");
        if (!messageFile.exists()) {
            logger.warn("message.json 文件不存在，跳过测试");
            return;
        }

        logger.info("=== 测试: 读取 message.json 发送请求 (自动限制 cache_control) ===");

        // 读取原始 JSON
        JsonNode rootNode = objectMapper.readTree(messageFile);

        // 统计原始 cache_control 数量
        int originalCount = countCacheControlBlocks(rootNode);
        logger.info("原始 cache_control 数量: {}", originalCount);

        // 转换为 ChatCompletionRequest
        ChatCompletionRequest request = convertToChatCompletionRequest(rootNode);
        request.setMaxTokens(500);

        // 发送请求 - ClaudeModelAdapter 会自动处理 cache_control 限制
        OpenAI client = createClient();

        logger.info("发送请求到 Bedrock (ClaudeModelAdapter 会自动限制 cache_control)...");

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder response = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        client.createChatCompletionStream(
            request,
            chunk -> {
                String content = chunk.getContent();
                if (content != null) {
                    System.out.print(content);
                    response.append(content);
                }
            },
            () -> {
                System.out.println("\n");
                logger.info("请求完成，响应长度: {} 字符", response.length());
                latch.countDown();
            },
            error -> {
                logger.error("请求失败", error);
                errorRef.set(error);
                latch.countDown();
            }
        );

        latch.await();

        if (errorRef.get() != null) {
            // 检查是否是 cache_control 限制错误
            String errorMsg = errorRef.get().getMessage();
            if (errorMsg != null && errorMsg.contains("cache_control")) {
                logger.error("仍然出现 cache_control 限制错误，请检查代码");
            }
            throw new RuntimeException("请求失败", errorRef.get());
        }

        logger.info("测试成功！cache_control 限制已被正确处理");
    }

    /**
     * 测试: 当启用 bedrockEnableSystemCache 时，如果已有 4 个 cache_control，
     * 代码应该自动移除 1 个以确保总数不超过 4
     */
    @Test
    public void testWithSystemCacheEnabled() throws Exception {
        File messageFile = new File("message.json");
        if (!messageFile.exists()) {
            logger.warn("message.json 文件不存在，跳过测试");
            return;
        }

        logger.info("=== 测试: 启用 bedrockEnableSystemCache (会额外增加一个 cache_control) ===");

        // 读取原始 JSON
        JsonNode rootNode = objectMapper.readTree(messageFile);

        // 统计原始 cache_control 数量
        int originalCount = countCacheControlBlocks(rootNode);
        logger.info("原始 cache_control 数量: {}", originalCount);
        logger.info("启用 bedrockEnableSystemCache 后预计总数: {}", originalCount + 1);

        // 转换为 ChatCompletionRequest
        ChatCompletionRequest request = convertToChatCompletionRequest(rootNode);
        request.setMaxTokens(200);
        request.setBedrockEnableSystemCache(true); // 启用 system 缓存，这会额外增加一个 cache_control

        // 发送请求
        OpenAI client = createClient();

        logger.info("发送请求到 Bedrock...");

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder response = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        client.createChatCompletionStream(
            request,
            chunk -> {
                String content = chunk.getContent();
                if (content != null) {
                    System.out.print(content);
                    response.append(content);
                }
            },
            () -> {
                System.out.println("\n");
                logger.info("请求完成，响应长度: {} 字符", response.length());
                latch.countDown();
            },
            error -> {
                logger.error("请求失败", error);
                errorRef.set(error);
                latch.countDown();
            }
        );

        latch.await();

        if (errorRef.get() != null) {
            // 检查是否是 cache_control 限制错误
            String errorMsg = errorRef.get().getMessage();
            if (errorMsg != null && errorMsg.contains("cache_control")) {
                logger.error("出现 cache_control 限制错误！自动限制逻辑可能有问题");
            }
            throw new RuntimeException("请求失败", errorRef.get());
        }

        logger.info("测试成功！即使启用了 bedrockEnableSystemCache，cache_control 限制也被正确处理");
    }

    /**
     * 递归统计 JSON 中 cache_control 块的数量
     */
    private int countCacheControlBlocks(JsonNode node) {
        int count = 0;

        if (node.isObject()) {
            if (node.has("cache_control")) {
                count++;
            }
            java.util.Iterator<JsonNode> elements = node.elements();
            while (elements.hasNext()) {
                count += countCacheControlBlocks(elements.next());
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                count += countCacheControlBlocks(element);
            }
        }

        return count;
    }

    /**
     * 将 JSON 转换为 ChatCompletionRequest
     */
    private ChatCompletionRequest convertToChatCompletionRequest(JsonNode rootNode) throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();

        // 设置模型
        if (rootNode.has("model")) {
            request.setModel(rootNode.get("model").asText());
        }

        // 转换消息
        if (rootNode.has("messages")) {
            List<ChatMessage> messages = new ArrayList<>();
            for (JsonNode msgNode : rootNode.get("messages")) {
                ChatMessage message = convertMessage(msgNode);
                if (message != null) {
                    messages.add(message);
                }
            }
            request.setMessages(messages);
        }

        return request;
    }

    /**
     * 转换单条消息
     */
    private ChatMessage convertMessage(JsonNode msgNode) {
        String role = msgNode.has("role") ? msgNode.get("role").asText() : "user";
        JsonNode contentNode = msgNode.get("content");

        if (contentNode == null) {
            return null;
        }

        ChatMessage message = new ChatMessage();
        message.setRole(role);

        if (contentNode.isTextual()) {
            // 简单文本内容
            message.setContent(contentNode.asText());
        } else if (contentNode.isArray()) {
            // 复杂内容 (ContentPart 数组)
            List<ChatMessage.ContentPart> parts = new ArrayList<>();
            for (JsonNode partNode : contentNode) {
                ChatMessage.ContentPart part = convertContentPart(partNode);
                if (part != null) {
                    parts.add(part);
                }
            }
            message.setContent(parts.toArray(new ChatMessage.ContentPart[0]));
        }

        return message;
    }

    /**
     * 转换 ContentPart
     */
    private ChatMessage.ContentPart convertContentPart(JsonNode partNode) {
        String type = partNode.has("type") ? partNode.get("type").asText() : "text";

        if ("text".equals(type)) {
            String text = partNode.has("text") ? partNode.get("text").asText() : "";

            // 检查是否有 cache_control
            if (partNode.has("cache_control")) {
                return ChatMessage.ContentPart.textWithCache(text, true);
            } else {
                return ChatMessage.ContentPart.text(text);
            }
        }

        return null;
    }
}
