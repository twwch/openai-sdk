package io.github.twwch.openai.sdk.service.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Claude模型适配器
 */
public class ClaudeModelAdapter implements BedrockModelAdapter {
    
    @Override
    public boolean supports(String modelId) {
        return modelId != null && modelId.contains("anthropic.claude");
    }
    
    @Override
    public String convertRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        ObjectNode bedrockRequest = objectMapper.createObjectNode();
        
        // 转换消息格式
        List<ChatMessage> messages = request.getMessages();
        StringBuilder systemPrompt = new StringBuilder();
        ArrayNode bedrockMessages = objectMapper.createArrayNode();
        
        for (ChatMessage message : messages) {
            if ("system".equals(message.getRole())) {
                if (systemPrompt.length() > 0) {
                    systemPrompt.append("\n\n");
                }
                systemPrompt.append(message.getContent());
            } else if ("tool".equals(message.getRole())) {
                // Bedrock不支持tool角色，需要转换为特定格式的user消息
                ObjectNode bedrockMessage = objectMapper.createObjectNode();
                bedrockMessage.put("role", "user");
                
                // 构建tool_result内容
                ArrayNode contentArray = objectMapper.createArrayNode();
                ObjectNode toolResult = objectMapper.createObjectNode();
                toolResult.put("type", "tool_result");
                toolResult.put("tool_use_id", message.getToolCallId());
                toolResult.put("content", message.getContent());
                contentArray.add(toolResult);
                
                bedrockMessage.set("content", contentArray);
                bedrockMessages.add(bedrockMessage);
            } else {
                ObjectNode bedrockMessage = objectMapper.createObjectNode();
                bedrockMessage.put("role", convertRole(message.getRole()));
                
                // 检查是否包含工具调用
                if (message.getToolCalls() != null && message.getToolCalls().length > 0) {
                    // 需要构建复杂的content数组
                    ArrayNode contentArray = objectMapper.createArrayNode();
                    
                    // 添加文本内容
                    if (message.getContent() != null && !message.getContent().isEmpty()) {
                        ObjectNode textContent = objectMapper.createObjectNode();
                        textContent.put("type", "text");
                        textContent.put("text", message.getContent());
                        contentArray.add(textContent);
                    }
                    
                    // 添加工具调用
                    for (ChatMessage.ToolCall toolCall : message.getToolCalls()) {
                        ObjectNode toolUse = objectMapper.createObjectNode();
                        toolUse.put("type", "tool_use");
                        toolUse.put("id", toolCall.getId());
                        toolUse.put("name", toolCall.getFunction().getName());
                        
                        // 解析工具参数
                        if (toolCall.getFunction().getArguments() != null) {
                            try {
                                JsonNode args = objectMapper.readTree(toolCall.getFunction().getArguments());
                                toolUse.set("input", args);
                            } catch (Exception e) {
                                // 如果解析失败，使用空对象
                                toolUse.set("input", objectMapper.createObjectNode());
                            }
                        }
                        
                        contentArray.add(toolUse);
                    }
                    
                    bedrockMessage.set("content", contentArray);
                } else {
                    // 普通消息
                    bedrockMessage.put("content", message.getContent());
                }
                
                bedrockMessages.add(bedrockMessage);
            }
        }
        
        // 构建请求
        bedrockRequest.set("messages", bedrockMessages);
        
        // 设置系统提示
        if (systemPrompt.length() > 0) {
            bedrockRequest.put("system", systemPrompt.toString());
        }
        
        // 设置模型参数
        bedrockRequest.put("anthropic_version", "bedrock-2023-05-31");
        
        if (request.getMaxTokens() != null) {
            bedrockRequest.put("max_tokens", request.getMaxTokens());
        } else {
            bedrockRequest.put("max_tokens", 4096); // Claude默认值
        }
        
        // 只有在temperature不为null且不等于默认值1.0时才设置
        if (request.getTemperature() != null && !request.getTemperature().equals(1.0)) {
            bedrockRequest.put("temperature", request.getTemperature());
        }
        
        // 只有在top_p不为null且不等于默认值1.0时才设置
        if (request.getTopP() != null && !request.getTopP().equals(1.0)) {
            bedrockRequest.put("top_p", request.getTopP());
        }
        
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            ArrayNode stopSequences = objectMapper.createArrayNode();
            for (String stop : request.getStop()) {
                stopSequences.add(stop);
            }
            bedrockRequest.set("stop_sequences", stopSequences);
        }
        
        // 转换工具定义
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            ArrayNode bedrockTools = objectMapper.createArrayNode();
            
            for (ChatCompletionRequest.Tool tool : request.getTools()) {
                if ("function".equals(tool.getType()) && tool.getFunction() != null) {

                    ObjectNode bedrockTool = objectMapper.createObjectNode();
                    ChatCompletionRequest.Function function = tool.getFunction();
                    
                    bedrockTool.put("name", function.getName());
                    bedrockTool.put("description", function.getDescription());
                    
                    // 转换参数schema
                    if (function.getParameters() != null) {
                        bedrockTool.set("input_schema", objectMapper.valueToTree(function.getParameters()));
                    }
                    bedrockTools.add(bedrockTool);
                }
            }
            
            if (bedrockTools.size() > 0) {
                bedrockRequest.set("tools", bedrockTools);
                System.out.println("添加了 " + bedrockTools.size() + " 个工具到请求中");
            }
        }
        
        // 设置tool_choice - 只有在有tools时才设置
        if (request.getToolChoice() != null && request.getTools() != null && !request.getTools().isEmpty()) {
            // Bedrock使用不同的tool_choice格式
            // "auto" -> {"type": "auto"}
            // "none" -> {"type": "none"}
            // 特定工具 -> {"type": "tool", "name": "tool_name"}
            ObjectNode toolChoice = objectMapper.createObjectNode();
            if ("auto".equals(request.getToolChoice())) {
                toolChoice.put("type", "auto");
            } else if ("none".equals(request.getToolChoice())) {
                toolChoice.put("type", "none");
            } else if (request.getToolChoice() instanceof String) {
                toolChoice.put("type", "tool");
                toolChoice.put("name", (String) request.getToolChoice());
            }
            bedrockRequest.set("tool_choice", toolChoice);
        }
        
        return objectMapper.writeValueAsString(bedrockRequest);
    }
    
    @Override
    public String convertStreamRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        // Claude的流式请求格式与非流式相同
        return convertRequest(request, objectMapper);
    }
    
    @Override
    public ChatCompletionResponse convertResponse(String bedrockResponse, ChatCompletionRequest originalRequest, ObjectMapper objectMapper) throws Exception {
        JsonNode responseNode = objectMapper.readTree(bedrockResponse);
        
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setId("chatcmpl-" + UUID.randomUUID().toString());
        response.setObject("chat.completion");
        response.setCreated(System.currentTimeMillis() / 1000);
        response.setModel(originalRequest.getModel());
        
        // 创建选择项
        List<ChatCompletionResponse.Choice> choices = new ArrayList<>();
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice();
        choice.setIndex(0);
        
        // 创建消息
        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        
        // 提取内容和工具调用
        JsonNode contentNode = responseNode.get("content");
        StringBuilder textContent = new StringBuilder();
        List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
        
        if (contentNode != null && contentNode.isArray()) {
            for (JsonNode content : contentNode) {
                String type = content.has("type") ? content.get("type").asText() : "text";
                
                if ("text".equals(type) && content.has("text")) {
                    textContent.append(content.get("text").asText());
                } else if ("tool_use".equals(type)) {
                    // 转换工具调用
                    ChatMessage.ToolCall toolCall = new ChatMessage.ToolCall();
                    toolCall.setId(content.has("id") ? content.get("id").asText() : UUID.randomUUID().toString());
                    toolCall.setType("function");
                    
                    ChatMessage.ToolCall.Function function = new ChatMessage.ToolCall.Function();
                    function.setName(content.has("name") ? content.get("name").asText() : "");
                    
                    // 转换参数
                    if (content.has("input")) {
                        function.setArguments(objectMapper.writeValueAsString(content.get("input")));
                    }
                    
                    toolCall.setFunction(function);
                    toolCalls.add(toolCall);
                }
            }
        } else if (responseNode.has("completion")) {
            textContent.append(responseNode.get("completion").asText());
        }
        
        if (textContent.length() > 0) {
            message.setContent(textContent.toString());
        } else if (contentNode == null || (contentNode.isArray() && contentNode.size() == 0)) {
            // 处理空content的情况，可能是因为stop_sequence
            message.setContent("");
        }
        
        if (!toolCalls.isEmpty()) {
            message.setToolCalls(toolCalls.toArray(new ChatMessage.ToolCall[0]));
        }
        
        choice.setMessage(message);
        
        // 设置完成原因
        if (responseNode.has("stop_reason")) {
            String stopReason = responseNode.get("stop_reason").asText();
            choice.setFinishReason(convertFinishReason(stopReason));
        }
        
        choices.add(choice);
        response.setChoices(choices);
        
        // 设置使用情况
        ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
        
        if (responseNode.has("usage")) {
            JsonNode usageNode = responseNode.get("usage");
            if (usageNode.has("input_tokens")) {
                usage.setPromptTokens(usageNode.get("input_tokens").asInt());
            }
            if (usageNode.has("output_tokens")) {
                usage.setCompletionTokens(usageNode.get("output_tokens").asInt());
            }
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
        }
        
        response.setUsage(usage);
        
        return response;
    }
    
    @Override
    public List<ChatCompletionChunk> convertStreamChunk(String chunk, ObjectMapper objectMapper) throws Exception {
        List<ChatCompletionChunk> chunks = new ArrayList<>();
        
        // Claude的流式响应格式
        String[] lines = chunk.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            try {
                JsonNode chunkNode = objectMapper.readTree(line);
                
                ChatCompletionChunk completionChunk = new ChatCompletionChunk();
                completionChunk.setId("chatcmpl-" + UUID.randomUUID().toString());
                completionChunk.setObject("chat.completion.chunk");
                completionChunk.setCreated(System.currentTimeMillis() / 1000);
                completionChunk.setModel("claude");
                
                List<ChatCompletionChunk.Choice> choices = new ArrayList<>();
                ChatCompletionChunk.Choice choice = new ChatCompletionChunk.Choice();
                choice.setIndex(0);
                
                ChatCompletionChunk.Delta delta = new ChatCompletionChunk.Delta();
                
                // 处理不同类型的事件
                if (chunkNode.has("type")) {
                    String type = chunkNode.get("type").asText();
                    
                    
                    if ("content_block_delta".equals(type)) {
                        JsonNode deltaNode = chunkNode.get("delta");
                        if (deltaNode != null && deltaNode.has("text")) {
                            delta.setContent(deltaNode.get("text").asText());
                        }
                    } else if ("message_start".equals(type)) {
                        delta.setRole("assistant");
                        // 检查是否有usage信息
                        if (chunkNode.has("message") && chunkNode.get("message").has("usage")) {
                            JsonNode usageNode = chunkNode.get("message").get("usage");
                            ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
                            if (usageNode.has("input_tokens")) {
                                usage.setPromptTokens(usageNode.get("input_tokens").asInt());
                            }
                            if (usageNode.has("output_tokens")) {
                                usage.setCompletionTokens(usageNode.get("output_tokens").asInt());
                            }
                            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
                            completionChunk.setUsage(usage);
                        }
                    } else if ("message_stop".equals(type)) {
                        choice.setFinishReason("stop");
                    } else if ("message_delta".equals(type)) {
                        // message_delta事件包含累积的token计数
                        if (chunkNode.has("usage")) {
                            JsonNode usageNode = chunkNode.get("usage");
                            ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
                            // message_delta中的usage是累积的，包含input_tokens和output_tokens
                            if (usageNode.has("input_tokens")) {
                                usage.setPromptTokens(usageNode.get("input_tokens").asInt());
                            }
                            if (usageNode.has("output_tokens")) {
                                usage.setCompletionTokens(usageNode.get("output_tokens").asInt());
                            }
                            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
                            completionChunk.setUsage(usage);
                        }
                        
                        // 处理stop_reason等其他delta字段
                        if (chunkNode.has("delta")) {
                            JsonNode deltaNode = chunkNode.get("delta");
                            if (deltaNode.has("stop_reason")) {
                                String stopReason = deltaNode.get("stop_reason").asText();
                                choice.setFinishReason(convertFinishReason(stopReason));
                            }
                        }
                    }
                }
                
                choice.setDelta(delta);
                choices.add(choice);
                completionChunk.setChoices(choices);
                
                chunks.add(completionChunk);
                
            } catch (Exception e) {
                // 忽略解析错误的行
            }
        }
        
        return chunks;
    }
    
    private String convertRole(String openAIRole) {
        if ("system".equals(openAIRole)) {
            return "user"; // Claude将system消息作为第一个user消息
        }
        return openAIRole;
    }
    
    private String convertFinishReason(String claudeReason) {
        switch (claudeReason) {
            case "end_turn":
            case "stop_sequence":
                return "stop";
            case "max_tokens":
                return "length";
            default:
                return claudeReason;
        }
    }
}