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
 * Cohere模型适配器
 */
public class CohereModelAdapter implements BedrockModelAdapter {
    
    @Override
    public boolean supports(String modelId) {
        return modelId != null && modelId.contains("cohere.");
    }
    
    @Override
    public String convertRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        ObjectNode bedrockRequest = objectMapper.createObjectNode();
        
        // 判断是Command R/R+模型还是旧版Command模型
        boolean isCommandR = request.getModel() != null && 
            (request.getModel().contains("command-r") || request.getModel().contains("commandr"));
        
        if (isCommandR) {
            // Command R/R+ 使用message格式
            StringBuilder message = new StringBuilder();
            List<ChatMessage> messages = request.getMessages();
            
            for (ChatMessage msg : messages) {
                if ("system".equals(msg.getRole())) {
                    message.append("System: ").append(msg.getContent()).append("\n\n");
                } else if ("user".equals(msg.getRole())) {
                    message.append("User: ").append(msg.getContent()).append("\n\n");
                } else if ("assistant".equals(msg.getRole())) {
                    message.append("Assistant: ").append(msg.getContent()).append("\n\n");
                }
            }
            
            bedrockRequest.put("message", message.toString().trim());
        } else {
            // 旧版Command使用prompt格式
            StringBuilder prompt = new StringBuilder();
            List<ChatMessage> messages = request.getMessages();
            
            for (ChatMessage msg : messages) {
                if ("system".equals(msg.getRole())) {
                    prompt.append("System: ").append(msg.getContent()).append("\n\n");
                } else if ("user".equals(msg.getRole())) {
                    prompt.append("User: ").append(msg.getContent()).append("\n\n");
                } else if ("assistant".equals(msg.getRole())) {
                    prompt.append("Assistant: ").append(msg.getContent()).append("\n\n");
                }
            }
            
            bedrockRequest.put("prompt", prompt.toString());
        }
        
        // 设置参数
        if (request.getMaxTokens() != null) {
            bedrockRequest.put("max_tokens", request.getMaxTokens());
        } else {
            bedrockRequest.put("max_tokens", 512);
        }
        
        if (request.getTemperature() != null) {
            bedrockRequest.put("temperature", request.getTemperature());
        }
        
        if (request.getTopP() != null) {
            bedrockRequest.put("p", request.getTopP());
        }
        
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            ArrayNode stopSequences = bedrockRequest.putArray("stop_sequences");
            for (String stop : request.getStop()) {
                stopSequences.add(stop);
            }
        }
        
        // 流式参数
        if (Boolean.TRUE.equals(request.getStream())) {
            bedrockRequest.put("stream", true);
        }
        
        return objectMapper.writeValueAsString(bedrockRequest);
    }
    
    @Override
    public String convertStreamRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        // Cohere支持流式响应，使用相同的请求格式
        request.setStream(true);
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
        
        // 判断响应格式
        if (responseNode.has("text")) {
            // Command R/R+ 格式
            message.setContent(responseNode.get("text").asText());
            if (responseNode.has("finish_reason")) {
                choice.setFinishReason(convertFinishReason(responseNode.get("finish_reason").asText()));
            }
        } else if (responseNode.has("generations") && responseNode.get("generations").isArray()) {
            // 旧版Command格式
            JsonNode generations = responseNode.get("generations");
            if (generations.size() > 0) {
                JsonNode firstGeneration = generations.get(0);
                if (firstGeneration.has("text")) {
                    message.setContent(firstGeneration.get("text").asText());
                }
                if (firstGeneration.has("finish_reason")) {
                    choice.setFinishReason(convertFinishReason(firstGeneration.get("finish_reason").asText()));
                }
            }
        }
        
        choice.setMessage(message);
        choices.add(choice);
        response.setChoices(choices);
        
        // Usage信息通常在HTTP headers中，这里设置默认值
        ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
        usage.setPromptTokens(0);
        usage.setCompletionTokens(0);
        usage.setTotalTokens(0);
        response.setUsage(usage);
        
        return response;
    }
    
    @Override
    public List<ChatCompletionChunk> convertStreamChunk(String chunk, ObjectMapper objectMapper) throws Exception {
        List<ChatCompletionChunk> chunks = new ArrayList<>();
        
        try {
            JsonNode chunkNode = objectMapper.readTree(chunk);
            
            ChatCompletionChunk completionChunk = new ChatCompletionChunk();
            completionChunk.setId("chatcmpl-" + UUID.randomUUID().toString());
            completionChunk.setObject("chat.completion.chunk");
            completionChunk.setCreated(System.currentTimeMillis() / 1000);
            completionChunk.setModel("cohere");
            
            List<ChatCompletionChunk.Choice> choices = new ArrayList<>();
            ChatCompletionChunk.Choice choice = new ChatCompletionChunk.Choice();
            choice.setIndex(0);
            
            ChatCompletionChunk.Delta delta = new ChatCompletionChunk.Delta();
            
            // 处理流式响应
            if (chunkNode.has("text")) {
                delta.setContent(chunkNode.get("text").asText());
            }
            
            if (chunkNode.has("finish_reason")) {
                choice.setFinishReason(convertFinishReason(chunkNode.get("finish_reason").asText()));
            } else if (chunkNode.has("is_finished") && chunkNode.get("is_finished").asBoolean()) {
                choice.setFinishReason("stop");
            }
            
            // Cohere的token信息通常在HTTP headers中，不在响应体中
            
            choice.setDelta(delta);
            choices.add(choice);
            completionChunk.setChoices(choices);
            
            chunks.add(completionChunk);
            
        } catch (Exception e) {
            // 忽略解析错误
        }
        
        return chunks;
    }
    
    private String convertFinishReason(String cohereReason) {
        switch (cohereReason) {
            case "COMPLETE":
            case "END_TURN":
                return "stop";
            case "MAX_TOKENS":
                return "length";
            default:
                return cohereReason.toLowerCase();
        }
    }
}