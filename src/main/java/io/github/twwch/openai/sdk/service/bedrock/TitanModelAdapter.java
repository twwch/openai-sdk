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
 * Amazon Titan模型适配器
 */
public class TitanModelAdapter implements BedrockModelAdapter {
    
    @Override
    public boolean supports(String modelId) {
        return modelId != null && modelId.contains("amazon.titan");
    }
    
    @Override
    public String convertRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        ObjectNode bedrockRequest = objectMapper.createObjectNode();
        
        // 构建输入文本
        StringBuilder inputText = new StringBuilder();
        List<ChatMessage> messages = request.getMessages();
        
        for (ChatMessage message : messages) {
            if ("system".equals(message.getRole())) {
                inputText.append("System: ").append(message.getContent()).append("\n\n");
            } else if ("user".equals(message.getRole())) {
                inputText.append("User: ").append(message.getContent()).append("\n\n");
            } else if ("assistant".equals(message.getRole())) {
                inputText.append("Assistant: ").append(message.getContent()).append("\n\n");
            }
        }
        inputText.append("Assistant: ");
        
        bedrockRequest.put("inputText", inputText.toString());
        
        // 设置文本生成配置
        ObjectNode textGenerationConfig = objectMapper.createObjectNode();
        
        if (request.getMaxTokens() != null) {
            textGenerationConfig.put("maxTokenCount", request.getMaxTokens());
        } else {
            textGenerationConfig.put("maxTokenCount", 512);
        }
        
        if (request.getTemperature() != null) {
            textGenerationConfig.put("temperature", request.getTemperature());
        }
        
        if (request.getTopP() != null) {
            textGenerationConfig.put("topP", request.getTopP());
        }
        
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            ArrayNode stopSequences = textGenerationConfig.putArray("stopSequences");
            for (String stop : request.getStop()) {
                stopSequences.add(stop);
            }
        }
        
        bedrockRequest.set("textGenerationConfig", textGenerationConfig);
        
        return objectMapper.writeValueAsString(bedrockRequest);
    }
    
    @Override
    public String convertStreamRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        // Titan支持流式响应，请求格式相同
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
        
        // 提取结果
        if (responseNode.has("results") && responseNode.get("results").isArray() && responseNode.get("results").size() > 0) {
            JsonNode firstResult = responseNode.get("results").get(0);
            if (firstResult.has("outputText")) {
                message.setContent(firstResult.get("outputText").asText());
            }
            if (firstResult.has("completionReason")) {
                choice.setFinishReason(convertFinishReason(firstResult.get("completionReason").asText()));
            }
        }
        
        choice.setMessage(message);
        choices.add(choice);
        response.setChoices(choices);
        
        // 设置使用情况
        ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
        
        if (responseNode.has("inputTextTokenCount")) {
            usage.setPromptTokens(responseNode.get("inputTextTokenCount").asInt());
        }
        
        if (responseNode.has("results") && responseNode.get("results").isArray() && responseNode.get("results").size() > 0) {
            JsonNode firstResult = responseNode.get("results").get(0);
            if (firstResult.has("tokenCount")) {
                usage.setCompletionTokens(firstResult.get("tokenCount").asInt());
            }
        }
        
        usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
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
            completionChunk.setModel("titan");
            
            List<ChatCompletionChunk.Choice> choices = new ArrayList<>();
            ChatCompletionChunk.Choice choice = new ChatCompletionChunk.Choice();
            choice.setIndex(0);
            
            ChatCompletionChunk.Delta delta = new ChatCompletionChunk.Delta();
            
            // Titan流式响应格式
            if (chunkNode.has("outputText")) {
                delta.setContent(chunkNode.get("outputText").asText());
            }
            
            if (chunkNode.has("completionReason")) {
                choice.setFinishReason(convertFinishReason(chunkNode.get("completionReason").asText()));
            }
            
            // 处理usage信息
            if (chunkNode.has("inputTextTokenCount") || chunkNode.has("totalOutputTextTokenCount")) {
                ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
                
                if (chunkNode.has("inputTextTokenCount")) {
                    usage.setPromptTokens(chunkNode.get("inputTextTokenCount").asInt());
                } else {
                    usage.setPromptTokens(0);
                }
                
                if (chunkNode.has("totalOutputTextTokenCount")) {
                    usage.setCompletionTokens(chunkNode.get("totalOutputTextTokenCount").asInt());
                } else {
                    usage.setCompletionTokens(0);
                }
                
                usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
                completionChunk.setUsage(usage);
            }
            
            choice.setDelta(delta);
            choices.add(choice);
            completionChunk.setChoices(choices);
            
            chunks.add(completionChunk);
            
        } catch (Exception e) {
            // 忽略解析错误
        }
        
        return chunks;
    }
    
    private String convertFinishReason(String titanReason) {
        switch (titanReason) {
            case "FINISH":
                return "stop";
            case "LENGTH":
                return "length";
            default:
                return titanReason.toLowerCase();
        }
    }
}