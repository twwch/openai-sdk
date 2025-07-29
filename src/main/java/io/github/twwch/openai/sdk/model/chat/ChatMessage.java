package io.github.twwch.openai.sdk.model.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 聊天消息
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {
    private String role;
    private String content;
    private String name;
    
    @JsonProperty("tool_call_id")
    private String toolCallId;

    
    @JsonProperty("function_call")
    private FunctionCall functionCall;
    
    @JsonProperty("tool_calls")
    private ToolCall[] toolCalls;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public FunctionCall getFunctionCall() {
        return functionCall;
    }

    public void setFunctionCall(FunctionCall functionCall) {
        this.functionCall = functionCall;
    }

    public ToolCall[] getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(ToolCall[] toolCalls) {
        this.toolCalls = toolCalls;
    }

    /**
     * 创建系统消息
     * @param content 消息内容
     * @return 系统消息
     */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    /**
     * 创建用户消息
     * @param content 消息内容
     * @return 用户消息
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    /**
     * 创建助手消息
     * @param content 消息内容
     * @return 助手消息
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    /**
     * 创建函数消息
     * @param name 函数名称
     * @param content 函数返回内容
     * @return 函数消息
     */
    public static ChatMessage function(String name, String content) {
        ChatMessage message = new ChatMessage("function", content);
        message.setName(name);
        return message;
    }

    /**
     * 创建工具消息
     * @param toolCallId 工具调用ID
     * @param content 工具返回内容
     * @return 工具消息
     */
    public static ChatMessage tool(String toolCallId, String content) {
        ChatMessage message = new ChatMessage("tool", content);
        message.setToolCallId(toolCallId);
        return message;
    }
    
    /**
     * 创建工具消息（带名称，用于兼容旧版本）
     * @param name 工具名称
     * @param toolCallId 工具调用ID
     * @param content 工具返回内容
     * @return 工具消息
     */
    public static ChatMessage tool(String name, String toolCallId, String content) {
        ChatMessage message = new ChatMessage("tool", content);
        message.setName(name);
        message.setToolCallId(toolCallId);
        return message;
    }

    /**
     * 函数调用
     */
    public static class FunctionCall {
        private String name;
        private String arguments;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArguments() {
            return arguments;
        }

        public void setArguments(String arguments) {
            this.arguments = arguments;
        }
    }

    /**
     * 工具调用
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCall {
        private String id;
        private String type;
        private Function function;
        private Integer index; // 流式响应中的索引

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

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
        
        public Integer getIndex() {
            return index;
        }
        
        public void setIndex(Integer index) {
            this.index = index;
        }

        /**
         * 函数定义
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Function {
            private String name;
            private String arguments;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getArguments() {
                return arguments;
            }

            public void setArguments(String arguments) {
                this.arguments = arguments;
            }
        }
    }
}