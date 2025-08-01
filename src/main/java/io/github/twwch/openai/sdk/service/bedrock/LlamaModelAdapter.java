package io.github.twwch.openai.sdk.service.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Llama模型适配器
 */
public class LlamaModelAdapter implements BedrockModelAdapter {
    
    @Override
    public boolean supports(String modelId) {
        return modelId != null && modelId.contains("meta.llama");
    }
    
    @Override
    public String convertRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        ObjectNode bedrockRequest = objectMapper.createObjectNode();
        
        // 构建prompt
        StringBuilder prompt = new StringBuilder();
        List<ChatMessage> messages = request.getMessages();
        
        for (ChatMessage message : messages) {
            if ("system".equals(message.getRole())) {
                prompt.append("<s>[INST] <<SYS>>\n");
                prompt.append(message.getContent());
                prompt.append("\n<</SYS>>\n\n");
            } else if ("user".equals(message.getRole())) {
                if (prompt.length() == 0) {
                    prompt.append("<s>[INST] ");
                }
                prompt.append(message.getContent());
                prompt.append(" [/INST]");
            } else if ("assistant".equals(message.getRole())) {
                prompt.append(" ").append(message.getContent()).append(" </s><s>[INST] ");
            }
        }
        
        bedrockRequest.put("prompt", prompt.toString());
        
        // 设置模型参数
        if (request.getMaxTokens() != null) {
            bedrockRequest.put("max_gen_len", request.getMaxTokens());
        } else {
            bedrockRequest.put("max_gen_len", 512);
        }
        
        if (request.getTemperature() != null) {
            bedrockRequest.put("temperature", request.getTemperature());
        }
        
        if (request.getTopP() != null) {
            bedrockRequest.put("top_p", request.getTopP());
        }
        
        return objectMapper.writeValueAsString(bedrockRequest);
    }
    
    @Override
    public String convertStreamRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        // Llama不支持流式响应
        throw new UnsupportedOperationException("Llama模型不支持流式响应");
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
        
        if (responseNode.has("generation")) {
            message.setContent(responseNode.get("generation").asText());
        }
        
        choice.setMessage(message);
        choice.setFinishReason("stop");
        
        choices.add(choice);
        response.setChoices(choices);
        
        // 设置使用情况（Llama不提供token统计）
        ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
        usage.setPromptTokens(0);
        usage.setCompletionTokens(0);
        usage.setTotalTokens(0);
        response.setUsage(usage);
        
        return response;
    }
    
    @Override
    public List<ChatCompletionChunk> convertStreamChunk(String chunk, ObjectMapper objectMapper) throws Exception {
        throw new UnsupportedOperationException("Llama模型不支持流式响应");
    }
}