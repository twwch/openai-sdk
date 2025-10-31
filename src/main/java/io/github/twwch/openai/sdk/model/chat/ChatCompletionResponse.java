package io.github.twwch.openai.sdk.model.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 聊天完成响应
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionResponse {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

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

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    /**
     * 获取第一个选择的消息内容
     * @return 消息内容
     */
    public String getContent() {
        if (choices != null && !choices.isEmpty() && choices.get(0).getMessage() != null) {
            return choices.get(0).getMessage().getContentAsString();
        }
        return null;
    }

    /**
     * 获取第一个选择的消息
     * @return 消息
     */
    public ChatMessage getMessage() {
        if (choices != null && !choices.isEmpty()) {
            return choices.get(0).getMessage();
        }
        return null;
    }

    /**
     * 选择
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private int index;
        private ChatMessage message;
        
        @JsonProperty("finish_reason")
        private String finishReason;
        
        private Object logprobs;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public ChatMessage getMessage() {
            return message;
        }

        public void setMessage(ChatMessage message) {
            this.message = message;
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
    }

    /**
     * 使用情况
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        @JsonProperty("completion_tokens")
        private int completionTokens;

        @JsonProperty("total_tokens")
        private int totalTokens;

        /**
         * Bedrock Prompt Caching: 从缓存读取的token数量
         * 这些token享受90%折扣
         */
        @JsonProperty("cache_read_input_tokens")
        private Integer cacheReadInputTokens;

        /**
         * Bedrock Prompt Caching: 写入缓存的token数量
         * 首次处理时比标准输入token价格高约25%
         */
        @JsonProperty("cache_creation_input_tokens")
        private Integer cacheCreationInputTokens;

        /**
         * Azure OpenAI: prompt tokens详细信息(包含缓存信息)
         */
        @JsonProperty("prompt_tokens_details")
        private PromptTokensDetails promptTokensDetails;

        /**
         * Azure OpenAI: completion tokens详细信息
         */
        @JsonProperty("completion_tokens_details")
        private CompletionTokensDetails completionTokensDetails;

        public int getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
        }

        /**
         * 获取缓存读取的token数量
         * 兼容Bedrock和Azure两种格式
         */
        public Integer getCacheReadInputTokens() {
            // 优先返回Bedrock格式
            if (cacheReadInputTokens != null) {
                return cacheReadInputTokens;
            }
            // 然后尝试Azure格式
            if (promptTokensDetails != null && promptTokensDetails.getCachedTokens() != null) {
                return promptTokensDetails.getCachedTokens();
            }
            return null;
        }

        public void setCacheReadInputTokens(Integer cacheReadInputTokens) {
            this.cacheReadInputTokens = cacheReadInputTokens;
        }

        public Integer getCacheCreationInputTokens() {
            return cacheCreationInputTokens;
        }

        public void setCacheCreationInputTokens(Integer cacheCreationInputTokens) {
            this.cacheCreationInputTokens = cacheCreationInputTokens;
        }

        public PromptTokensDetails getPromptTokensDetails() {
            return promptTokensDetails;
        }

        public void setPromptTokensDetails(PromptTokensDetails promptTokensDetails) {
            this.promptTokensDetails = promptTokensDetails;
        }

        public CompletionTokensDetails getCompletionTokensDetails() {
            return completionTokensDetails;
        }

        public void setCompletionTokensDetails(CompletionTokensDetails completionTokensDetails) {
            this.completionTokensDetails = completionTokensDetails;
        }
    }

    /**
     * Azure OpenAI prompt tokens详细信息
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromptTokensDetails {
        @JsonProperty("cached_tokens")
        private Integer cachedTokens;

        @JsonProperty("audio_tokens")
        private Integer audioTokens;

        public Integer getCachedTokens() {
            return cachedTokens;
        }

        public void setCachedTokens(Integer cachedTokens) {
            this.cachedTokens = cachedTokens;
        }

        public Integer getAudioTokens() {
            return audioTokens;
        }

        public void setAudioTokens(Integer audioTokens) {
            this.audioTokens = audioTokens;
        }
    }

    /**
     * Azure OpenAI completion tokens详细信息
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CompletionTokensDetails {
        @JsonProperty("reasoning_tokens")
        private Integer reasoningTokens;

        @JsonProperty("audio_tokens")
        private Integer audioTokens;

        public Integer getReasoningTokens() {
            return reasoningTokens;
        }

        public void setReasoningTokens(Integer reasoningTokens) {
            this.reasoningTokens = reasoningTokens;
        }

        public Integer getAudioTokens() {
            return audioTokens;
        }

        public void setAudioTokens(Integer audioTokens) {
            this.audioTokens = audioTokens;
        }
    }
}