package com.openai.sdk.model.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聊天完成请求
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionRequest {
    private String model;
    private List<ChatMessage> messages;
    
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    
    private Double temperature;
    
    @JsonProperty("top_p")
    private Double topP;
    
    private Integer n;
    
    private Boolean stream;
    
    private List<String> stop;
    
    @JsonProperty("presence_penalty")
    private Double presencePenalty;
    
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;
    
    @JsonProperty("logit_bias")
    private Map<String, Integer> logitBias;
    
    private String user;
    
    private List<Function> functions;
    
    @JsonProperty("function_call")
    private Object functionCall;
    
    private List<Tool> tools;
    
    @JsonProperty("tool_choice")
    private Object toolChoice;
    
    @JsonProperty("response_format")
    private ResponseFormat responseFormat;

    public ChatCompletionRequest() {
        this.messages = new ArrayList<>();
    }

    public ChatCompletionRequest(String model, List<ChatMessage> messages) {
        this.model = model;
        this.messages = messages;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getN() {
        return n;
    }

    public void setN(Integer n) {
        this.n = n;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public List<String> getStop() {
        return stop;
    }

    public void setStop(List<String> stop) {
        this.stop = stop;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Map<String, Integer> getLogitBias() {
        return logitBias;
    }

    public void setLogitBias(Map<String, Integer> logitBias) {
        this.logitBias = logitBias;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public List<Function> getFunctions() {
        return functions;
    }

    public void setFunctions(List<Function> functions) {
        this.functions = functions;
    }

    public Object getFunctionCall() {
        return functionCall;
    }

    public void setFunctionCall(Object functionCall) {
        this.functionCall = functionCall;
    }

    public List<Tool> getTools() {
        return tools;
    }

    public void setTools(List<Tool> tools) {
        this.tools = tools;
    }

    public Object getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
    }

    public ResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(ResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
    }

    /**
     * 函数定义
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {
        private String name;
        private String description;
        private Map<String, Object> parameters;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }
    }

    /**
     * 工具定义
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tool {
        private String type;
        private Function function;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Function getFunction() {
            return function;
        }

        public void setFunction(Function function) {
            this.function = function;
        }
    }

    /**
     * 响应格式
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseFormat {
        private String type;

        public ResponseFormat() {
        }

        public ResponseFormat(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}