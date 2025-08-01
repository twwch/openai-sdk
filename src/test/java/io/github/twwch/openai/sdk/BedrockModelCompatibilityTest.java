package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * 测试不同Claude模型的兼容性
 */
public class BedrockModelCompatibilityTest {
    
    public static void main(String[] args) throws Exception {
        // 从环境变量获取凭证
        String bearerKey = System.getenv("AWS_BEARER_KEY_BEDROCK");
        String bearerToken = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
        String modelId =  "us.anthropic.claude-3-7-sonnet-20250219-v1:0";

        if (bearerKey == null || bearerToken == null) {
            System.err.println("请设置环境变量 AWS_BEARER_KEY_BEDROCK 和 AWS_BEARER_TOKEN_BEDROCK");
            System.exit(1);
        }

        System.out.println("Bearer Key: " + bearerKey);
        System.out.println("Bearer Token: " + bearerToken);
        System.out.println("modelId: " + modelId);

        OpenAI client = OpenAI.bedrock("us-east-2", bearerKey, bearerToken, modelId);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user("计算 123 + 456 的结果"));

        // 创建计算工具
        List<ChatCompletionRequest.Tool> tools = new ArrayList<>();
        ChatCompletionRequest.Tool calcTool = new ChatCompletionRequest.Tool();
        calcTool.setType("function");

        ChatCompletionRequest.Function calcFunction = new ChatCompletionRequest.Function();
        calcFunction.setName("calculate");
        calcFunction.setDescription("执行数学计算");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> expression = new HashMap<>();
        expression.put("type", "string");
        expression.put("description", "要计算的数学表达式");
        properties.put("expression", expression);

        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("expression"));

        calcFunction.setParameters(parameters);
        calcTool.setFunction(calcFunction);
        tools.add(calcTool);

        // 创建流式请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(messages);
        request.setTools(tools);
        request.setStream(true);

        // 设置stream_options
        ChatCompletionRequest.StreamOptions streamOptions = new ChatCompletionRequest.StreamOptions(true);
        request.setStreamOptions(streamOptions);

        // 清除默认值
        request.setTemperature(null);
        request.setTopP(null);
        request.setN(null);
        request.setPresencePenalty(null);
        request.setFrequencyPenalty(null);
        request.setLogprobs(null);
        request.setServiceTier(null);

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder contentBuilder = new StringBuilder();
        final ChatCompletionResponse.Usage[] finalUsage = {null};

        System.out.println("\n流式响应:");

        client.createChatCompletionStream(
                request,
                chunk -> {
                    // 收集内容
                    String content = chunk.getContent();
                    if (content != null && !content.isEmpty()) {
                        System.out.print(content);
                        contentBuilder.append(content);
                    }

                    // 收集usage信息 - message_delta事件包含累积的usage
                    if (chunk.getUsage() != null) {
                        finalUsage[0] = chunk.getUsage();
                    }

                    // 检查是否是最后一个chunk
                    if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                        String finishReason = chunk.getChoices().get(0).getFinishReason();
                        if ("stop".equals(finishReason) && chunk.getUsage() == null && finalUsage[0] == null) {
                            // 如果是最后一个chunk但没有usage，可能需要从其他地方获取
                            System.out.println("\n[注意] 最后一个chunk没有包含usage信息");
                        }
                    }
                },
                () -> {
                    System.out.println("\n\n流式响应完成");

                    // 流式响应中的工具调用需要特别处理
                    // Bedrock的流式响应可能不会返回工具调用
                    String fullContent = contentBuilder.toString();
                    System.out.println("\n完整响应内容: " + fullContent);

                    // 显示usage信息
                    if (finalUsage[0] != null) {
                        System.out.println("\nUsage信息:");
                        System.out.println("  输入tokens: " + finalUsage[0].getPromptTokens());
                        System.out.println("  输出tokens: " + finalUsage[0].getCompletionTokens());
                        System.out.println("  总tokens: " + finalUsage[0].getTotalTokens());
                    } else {
                        System.out.println("\n未获取到Usage信息");
                    }

                    latch.countDown();
                },
                error -> {
                    System.err.println("错误: " + error.getMessage());
                    error.printStackTrace();
                    latch.countDown();
                }
        );

        latch.await();
    }
}