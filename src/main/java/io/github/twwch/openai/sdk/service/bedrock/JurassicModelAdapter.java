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
 * AI21 Jurassic模型适配器
 */
public class JurassicModelAdapter implements BedrockModelAdapter {
    
    @Override
    public boolean supports(String modelId) {
        return modelId != null && modelId.contains("ai21.j2");
    }
    
    @Override
    public String convertRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        ObjectNode bedrockRequest = objectMapper.createObjectNode();
        
        // 构建prompt
        StringBuilder prompt = new StringBuilder();
        List<ChatMessage> messages = request.getMessages();
        
        for (ChatMessage message : messages) {
            if ("system".equals(message.getRole())) {
                prompt.append("System: ").append(message.getContent()).append("\n\n");
            } else if ("user".equals(message.getRole())) {
                prompt.append("User: ").append(message.getContent()).append("\n\n");
            } else if ("assistant".equals(message.getRole())) {
                prompt.append("Assistant: ").append(message.getContent()).append("\n\n");
            }
        }
        
        // 添加最后的提示符，让模型继续生成
        if (messages.size() > 0) {
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            if ("user".equals(lastMessage.getRole())) {
                prompt.append("Assistant: ");
            }
        }
        
        bedrockRequest.put("prompt", prompt.toString());
        
        // 设置参数
        if (request.getMaxTokens() != null) {
            bedrockRequest.put("maxTokens", request.getMaxTokens());
        } else {
            bedrockRequest.put("maxTokens", 512);
        }
        
        if (request.getTemperature() != null) {
            bedrockRequest.put("temperature", request.getTemperature());
        } else {
            bedrockRequest.put("temperature", 0.7);
        }
        
        if (request.getTopP() != null) {
            bedrockRequest.put("topP", request.getTopP());
        } else {
            bedrockRequest.put("topP", 1.0);
        }
        
        // 设置停止序列
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            ArrayNode stopSequences = bedrockRequest.putArray("stopSequences");
            for (String stop : request.getStop()) {
                stopSequences.add(stop);
            }
        }
        
        // Jurassic特有的参数
        ObjectNode countPenalty = objectMapper.createObjectNode();
        countPenalty.put("scale", 0);
        bedrockRequest.set("countPenalty", countPenalty);
        
        ObjectNode presencePenalty = objectMapper.createObjectNode();
        presencePenalty.put("scale", 0);
        bedrockRequest.set("presencePenalty", presencePenalty);
        
        ObjectNode frequencyPenalty = objectMapper.createObjectNode();
        frequencyPenalty.put("scale", 0);
        bedrockRequest.set("frequencyPenalty", frequencyPenalty);
        
        return objectMapper.writeValueAsString(bedrockRequest);
    }
    
    @Override
    public String convertStreamRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        throw new UnsupportedOperationException("Jurassic模型不支持流式响应");
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
        
        // 提取生成的文本
        if (responseNode.has("completions") && responseNode.get("completions").isArray()) {
            JsonNode completions = responseNode.get("completions");
            if (completions.size() > 0) {
                JsonNode firstCompletion = completions.get(0);
                if (firstCompletion.has("data") && firstCompletion.get("data").has("text")) {
                    String generatedText = firstCompletion.get("data").get("text").asText();
                    // 清理可能的前导换行符
                    if (generatedText.startsWith("\n")) {
                        generatedText = generatedText.substring(1);
                    }
                    message.setContent(generatedText);
                }
                
                // 设置完成原因
                if (firstCompletion.has("finishReason")) {
                    choice.setFinishReason(convertFinishReason(firstCompletion.get("finishReason").asText()));
                } else {
                    choice.setFinishReason("stop");
                }
            }
        }
        
        choice.setMessage(message);
        choices.add(choice);
        response.setChoices(choices);
        
        // 设置使用情况
        ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
        
        // Jurassic响应包含token信息
        if (responseNode.has("prompt") && responseNode.get("prompt").has("tokens")) {
            JsonNode promptTokens = responseNode.get("prompt").get("tokens");
            if (promptTokens.isArray()) {
                usage.setPromptTokens(promptTokens.size());
            }
        }
        
        if (responseNode.has("completions") && responseNode.get("completions").isArray()) {
            JsonNode completions = responseNode.get("completions");
            if (completions.size() > 0) {
                JsonNode firstCompletion = completions.get(0);
                if (firstCompletion.has("data") && firstCompletion.get("data").has("tokens")) {
                    JsonNode completionTokens = firstCompletion.get("data").get("tokens");
                    if (completionTokens.isArray()) {
                        usage.setCompletionTokens(completionTokens.size());
                    }
                }
            }
        }
        
        usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
        response.setUsage(usage);
        
        return response;
    }
    
    @Override
    public List<ChatCompletionChunk> convertStreamChunk(String chunk, ObjectMapper objectMapper) throws Exception {
        throw new UnsupportedOperationException("Jurassic模型不支持流式响应");
    }
    
    private String convertFinishReason(String jurassicReason) {
        switch (jurassicReason) {
            case "endOfText":
            case "stop":
                return "stop";
            case "length":
                return "length";
            default:
                return jurassicReason.toLowerCase();
        }
    }
}