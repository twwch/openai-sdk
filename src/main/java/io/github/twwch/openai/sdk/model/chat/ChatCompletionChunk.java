package io.github.twwch.openai.sdk.model.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 聊天完成流式响应块
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionChunk {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<Choice> choices;
    private ChatCompletionResponse.Usage usage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public ChatCompletionResponse.Usage getUsage() {
        return usage;
    }

    public void setUsage(ChatCompletionResponse.Usage usage) {
        this.usage = usage;
    }

    /**
     * 获取第一个选择的内容增量
     * @return 内容增量
     */
    public String getContent() {
        if (choices != null && !choices.isEmpty() && choices.get(0).getDelta() != null) {
            return choices.get(0).getDelta().getContent();
        }
        return null;
    }

    /**
     * 选择
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private int index;
        private Delta delta;
        
        @JsonProperty("finish_reason")
        private String finishReason;
        
        private Object logprobs;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public Delta getDelta() {
            return delta;
        }

        public void setDelta(Delta delta) {
            this.delta = delta;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }

        public Object getLogprobs() {
            return logprobs;
        }

        public void setLogprobs(Object logprobs) {
            this.logprobs = logprobs;
        }

        public String toString() {
            return "Choice{" +
                    "index=" + index +
                    ", delta=" + delta +
                    ", finishReason='" + finishReason + '\'' +
                    ", logprobs=" + logprobs +
                    '}';
        }
    }

    /**
     * 增量
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {
        private String role;
        private String content;
        
        @JsonProperty("tool_calls")
        private List<ChatMessage.ToolCall> toolCalls;
        
        @JsonProperty("function_call")
        private ChatMessage.FunctionCall functionCall;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<ChatMessage.ToolCall> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<ChatMessage.ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
        }

        public ChatMessage.FunctionCall getFunctionCall() {
            return functionCall;
        }

        public void setFunctionCall(ChatMessage.FunctionCall functionCall) {
            this.functionCall = functionCall;
        }

        @Override
        public String toString() {
            return "Delta{" +
                    "role='" + role + '\'' +
                    ", content='" + content + '\'' +
                    ", toolCalls=" + toolCalls +
                    ", functionCall=" + functionCall +
                    '}';
        }
    }

    public String toString(){
        return "ChatCompletionChunk{" +
                "id='" + id + '\'' +
                ", object='" + object + '\'' +
                ", created=" + created +
                ", model='" + model + '\'' +
                ", choices=" + choices +
                ", usage=" + usage +
                '}';
    }
}