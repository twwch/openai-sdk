package io.github.twwch.openai.sdk.model.chat;

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
    
    @JsonProperty("stream_options")
    private StreamOptions streamOptions;
    
    /**
     * Parameters for audio output. Required when audio output is requested with modalities: ["audio"]
     */
    private AudioParams audio;
    
    /**
     * Whether to return log probabilities of the output tokens or not
     */
    private Boolean logprobs;
    
    /**
     * An upper bound for the number of tokens that can be generated for a completion
     */
    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;
    
    /**
     * Set of 16 key-value pairs that can be attached to an object
     */
    private Map<String, String> metadata;
    
    /**
     * Output types that you would like the model to generate
     */
    private List<String> modalities;
    
    /**
     * Whether to enable parallel function calling during tool use
     */
    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;
    
    /**
     * Configuration for a Predicted Output
     */
    private Prediction prediction;
    
    /**
     * Used by OpenAI to cache responses for similar requests
     */
    @JsonProperty("prompt_cache_key")
    private String promptCacheKey;
    
    /**
     * Constrains effort on reasoning for reasoning models (o-series models only)
     */
    @JsonProperty("reasoning_effort")
    private String reasoningEffort;
    
    /**
     * A stable identifier used to help detect users of your application
     */
    @JsonProperty("safety_identifier")
    private String safetyIdentifier;
    
    /**
     * This feature is in Beta. If specified, our system will make a best effort to sample deterministically
     */
    private Integer seed;
    
    /**
     * Specifies the processing type used for serving the request
     */
    @JsonProperty("service_tier")
    private String serviceTier;
    
    /**
     * Whether or not to store the output of this chat completion request
     */
    private Boolean store;
    
    /**
     * An integer between 0 and 20 specifying the number of most likely tokens to return at each token position
     */
    @JsonProperty("top_logprobs")
    private Integer topLogprobs;
    
    /**
     * This tool searches the web for relevant results to use in a response
     */
    @JsonProperty("web_search_options")
    private WebSearchOptions webSearchOptions;

    public ChatCompletionRequest() {
        this.messages = new ArrayList<>();
        // 设置默认值
        this.temperature = 1.0;
        this.topP = 1.0;
        this.n = 1;
        this.presencePenalty = 0.0;
        this.frequencyPenalty = 0.0;
        this.logprobs = false;
        this.serviceTier = "auto";
    }

    public ChatCompletionRequest(String model, List<ChatMessage> messages) {
        this.model = model;
        this.messages = messages;
        // 设置默认值
        if (model != null && model.toLowerCase().contains("o3")) {
            // o3 模型不设置这些默认值
            this.temperature = null;
            this.maxTokens = null;
            this.parallelToolCalls = null;
        } else {
            this.temperature = 1.0;
        }
        this.topP = 1.0;
        this.n = 1;
        this.presencePenalty = 0.0;
        this.frequencyPenalty = 0.0;
        this.logprobs = false;
        this.serviceTier = "auto";
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
        
        // 如果模型名称包含 "o3"，设置相关参数为 null
        if (model != null && model.toLowerCase().contains("o3")) {
            this.temperature = null;
            this.maxTokens = null;
            this.parallelToolCalls = null;
        }
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
        // 如果当前模型包含 "o3"，忽略此设置
        if (this.model != null && this.model.toLowerCase().contains("o3")) {
            return;
        }
        this.maxTokens = maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        // 如果当前模型包含 "o3"，忽略此设置
        if (this.model != null && this.model.toLowerCase().contains("o3")) {
            return;
        }
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
        // 如果当前模型包含 "o3"，不设置 parallelToolCalls
        if (this.model != null && this.model.toLowerCase().contains("o3")) {
            return;
        }
        // 当设置 tools 时，如果 parallelToolCalls 未设置，则默认为 true
        if (tools != null && !tools.isEmpty() && this.parallelToolCalls == null) {
            this.parallelToolCalls = true;
        }
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

    public StreamOptions getStreamOptions() {
        return streamOptions;
    }

    public void setStreamOptions(StreamOptions streamOptions) {
        this.streamOptions = streamOptions;
    }

    public AudioParams getAudio() {
        return audio;
    }

    public void setAudio(AudioParams audio) {
        this.audio = audio;
    }

    public Boolean getLogprobs() {
        return logprobs;
    }

    public void setLogprobs(Boolean logprobs) {
        this.logprobs = logprobs;
    }

    public Integer getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(Integer maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public List<String> getModalities() {
        return modalities;
    }

    public void setModalities(List<String> modalities) {
        this.modalities = modalities;
    }

    public Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    public void setParallelToolCalls(Boolean parallelToolCalls) {
        // 如果当前模型包含 "o3"，忽略此设置
        if (this.model != null && this.model.toLowerCase().contains("o3")) {
            return;
        }
        this.parallelToolCalls = parallelToolCalls;
    }

    public Prediction getPrediction() {
        return prediction;
    }

    public void setPrediction(Prediction prediction) {
        this.prediction = prediction;
    }

    public String getPromptCacheKey() {
        return promptCacheKey;
    }

    public void setPromptCacheKey(String promptCacheKey) {
        this.promptCacheKey = promptCacheKey;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public String getSafetyIdentifier() {
        return safetyIdentifier;
    }

    public void setSafetyIdentifier(String safetyIdentifier) {
        this.safetyIdentifier = safetyIdentifier;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    public String getServiceTier() {
        return serviceTier;
    }

    public void setServiceTier(String serviceTier) {
        this.serviceTier = serviceTier;
    }

    public Boolean getStore() {
        return store;
    }

    public void setStore(Boolean store) {
        this.store = store;
    }

    public Integer getTopLogprobs() {
        return topLogprobs;
    }

    public void setTopLogprobs(Integer topLogprobs) {
        this.topLogprobs = topLogprobs;
    }

    public WebSearchOptions getWebSearchOptions() {
        return webSearchOptions;
    }

    public void setWebSearchOptions(WebSearchOptions webSearchOptions) {
        this.webSearchOptions = webSearchOptions;
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
        
        @JsonProperty("json_schema")
        private JsonSchema jsonSchema;

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
        
        public JsonSchema getJsonSchema() {
            return jsonSchema;
        }
        
        public void setJsonSchema(JsonSchema jsonSchema) {
            this.jsonSchema = jsonSchema;
        }
        
        /**
         * JSON Schema 定义
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class JsonSchema {
            private String name;
            private Map<String, Object> schema;
            private Boolean strict;
            
            public String getName() {
                return name;
            }
            
            public void setName(String name) {
                this.name = name;
            }
            
            public Map<String, Object> getSchema() {
                return schema;
            }
            
            public void setSchema(Map<String, Object> schema) {
                this.schema = schema;
            }
            
            public Boolean getStrict() {
                return strict;
            }
            
            public void setStrict(Boolean strict) {
                this.strict = strict;
            }
        }
    }

    /**
     * 流式选项
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StreamOptions {
        @JsonProperty("include_usage")
        private Boolean includeUsage;

        public StreamOptions() {
        }

        public StreamOptions(boolean includeUsage) {
            this.includeUsage = includeUsage;
        }

        public Boolean getIncludeUsage() {
            return includeUsage;
        }

        public void setIncludeUsage(Boolean includeUsage) {
            this.includeUsage = includeUsage;
        }
    }

    /**
     * 音频参数
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AudioParams {
        private String voice;
        private String format;

        public String getVoice() {
            return voice;
        }

        public void setVoice(String voice) {
            this.voice = voice;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }

    /**
     * 预测输出配置
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Prediction {
        private String type;
        private String content;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 网络搜索选项
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WebSearchOptions {
        private Boolean enable;

        public Boolean getEnable() {
            return enable;
        }

        public void setEnable(Boolean enable) {
            this.enable = enable;
        }
    }
}