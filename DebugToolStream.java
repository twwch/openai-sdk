import io.github.twwch.openai.sdk.OpenAI;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class DebugToolStream {
    public static void main(String[] args) throws Exception {
        String region = "us-east-2";
        String accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
        String secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        String modelId = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";
        
        OpenAI openai = OpenAI.bedrock(region, accessKeyId, secretAccessKey, modelId);
        
        // 创建请求
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelId);
        request.setMessages(Arrays.asList(
            ChatMessage.user("What's the weather in Beijing?")
        ));
        
        // 创建天气工具
        ChatCompletionRequest.Tool weatherTool = new ChatCompletionRequest.Tool();
        weatherTool.setType("function");
        
        ChatCompletionRequest.Function weatherFunction = new ChatCompletionRequest.Function();
        weatherFunction.setName("get_current_weather");
        weatherFunction.setDescription("Get the current weather for a city");
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> cityProp = new HashMap<>();
        cityProp.put("type", "string");
        cityProp.put("description", "The city name");
        properties.put("city", cityProp);
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("city"));
        
        weatherFunction.setParameters(parameters);
        weatherTool.setFunction(weatherFunction);
        
        request.setTools(Arrays.asList(weatherTool));
        request.setToolChoice("auto");
        
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, StringBuilder> toolCallArgs = new HashMap<>();
        
        System.out.println("=== 调试工具流式响应 ===");
        
        openai.createChatCompletionStream(request,
            chunk -> {
                System.out.println("\n--- CHUNK ---");
                if (chunk.getContent() != null) {
                    System.out.println("Content: " + chunk.getContent());
                }
                
                if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                    ChatCompletionChunk.Delta delta = chunk.getChoices().get(0).getDelta();
                    if (delta != null && delta.getToolCalls() != null) {
                        for (ChatMessage.ToolCall toolCall : delta.getToolCalls()) {
                            System.out.println("ToolCall:");
                            System.out.println("  ID: " + toolCall.getId());
                            System.out.println("  Index: " + toolCall.getIndex());
                            System.out.println("  Type: " + toolCall.getType());
                            
                            if (toolCall.getFunction() != null) {
                                System.out.println("  Function:");
                                System.out.println("    Name: " + toolCall.getFunction().getName());
                                System.out.println("    Arguments: " + toolCall.getFunction().getArguments());
                                
                                // 累积参数
                                if (toolCall.getId() != null && toolCall.getFunction().getArguments() != null) {
                                    toolCallArgs.computeIfAbsent(toolCall.getId(), k -> new StringBuilder())
                                        .append(toolCall.getFunction().getArguments());
                                }
                            }
                        }
                    }
                }
            },
            () -> {
                System.out.println("\n=== 流式响应完成 ===");
                System.out.println("累积的工具调用参数:");
                toolCallArgs.forEach((id, args) -> {
                    System.out.println("ID: " + id);
                    System.out.println("Arguments: " + args.toString());
                });
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