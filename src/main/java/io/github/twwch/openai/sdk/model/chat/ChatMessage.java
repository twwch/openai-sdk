package io.github.twwch.openai.sdk.model.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 聊天消息
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {
    private String role;
    private Object content; // 支持String或ContentPart[]
    private String name;
    
    @JsonProperty("tool_call_id")
    private String toolCallId;

    
    @JsonProperty("function_call")
    private FunctionCall functionCall;
    
    @JsonProperty("tool_calls")
    private ToolCall[] toolCalls;

    public ChatMessage() {
    }

    public ChatMessage(String role, Object content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }
    
    // 便捷方法：获取字符串内容
    // 注意：添加@JsonIgnore防止序列化
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getContentAsString() {
        if (content instanceof String) {
            return (String) content;
        } else if (content instanceof ContentPart[]) {
            StringBuilder sb = new StringBuilder();
            for (ContentPart part : (ContentPart[]) content) {
                if ("text".equals(part.getType()) && part.getText() != null) {
                    sb.append(part.getText());
                }
            }
            return sb.toString();
        }
        return null;
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
     * 创建用户消息（文本）
     * @param content 消息内容
     * @return 用户消息
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }
    
    /**
     * 创建用户消息（多模态内容）
     * @param parts 内容部分数组
     * @return 用户消息
     */
    public static ChatMessage user(ContentPart... parts) {
        return new ChatMessage("user", parts);
    }

    /**
     * 创建助手消息（文本）
     * @param content 消息内容
     * @return 助手消息
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
    
    /**
     * 创建助手消息（多模态内容）
     * @param parts 内容部分数组
     * @return 助手消息
     */
    public static ChatMessage assistant(ContentPart... parts) {
        return new ChatMessage("assistant", parts);
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
     * 创建包含文本和图片的用户消息（便捷方法）
     * @param text 文本内容（可以为null或空字符串）
     * @param imageUrl 图片URL或base64编码
     * @return 用户消息
     */
    public static ChatMessage userWithImage(String text, String imageUrl) {
        // 根据text是否为空动态创建parts数组
        if (text != null && !text.trim().isEmpty()) {
            ContentPart[] parts = new ContentPart[] {
                ContentPart.text(text),
                ContentPart.imageUrl(imageUrl)
            };
            return new ChatMessage("user", parts);
        } else {
            // 只包含图片，不包含文本
            ContentPart[] parts = new ContentPart[] {
                ContentPart.imageUrl(imageUrl)
            };
            return new ChatMessage("user", parts);
        }
    }
    
    /**
     * 创建包含多张图片的用户消息（便捷方法）
     * @param text 文本内容（可以为null或空字符串）
     * @param imageUrls 图片URL或base64编码数组
     * @return 用户消息
     */
    public static ChatMessage userWithImages(String text, String... imageUrls) {
        // 根据text是否为空动态创建parts数组
        if (text != null && !text.trim().isEmpty()) {
            ContentPart[] parts = new ContentPart[imageUrls.length + 1];
            parts[0] = ContentPart.text(text);
            for (int i = 0; i < imageUrls.length; i++) {
                parts[i + 1] = ContentPart.imageUrl(imageUrls[i]);
            }
            return new ChatMessage("user", parts);
        } else {
            // 只包含图片，不包含文本
            ContentPart[] parts = new ContentPart[imageUrls.length];
            for (int i = 0; i < imageUrls.length; i++) {
                parts[i] = ContentPart.imageUrl(imageUrls[i]);
            }
            return new ChatMessage("user", parts);
        }
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
     * 内容部分 - 支持文本和图片
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentPart {
        private String type; // "text" 或 "image_url"
        private String text;
        @JsonProperty("image_url")
        private ImageUrl imageUrl;
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public ImageUrl getImageUrl() {
            return imageUrl;
        }
        
        public void setImageUrl(ImageUrl imageUrl) {
            this.imageUrl = imageUrl;
        }
        
        // 便捷创建方法
        public static ContentPart text(String text) {
            ContentPart part = new ContentPart();
            part.setType("text");
            part.setText(text);
            return part;
        }
        
        public static ContentPart imageUrl(String url) {
            ContentPart part = new ContentPart();
            part.setType("image_url");
            ImageUrl img = new ImageUrl();
            img.setUrl(url);
            part.setImageUrl(img);
            return part;
        }
        
        /**
         * 图片URL
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ImageUrl {
            private String url;
            private String detail; // "auto", "low", "high"
            
            public String getUrl() {
                return url;
            }
            
            public void setUrl(String url) {
                this.url = url;
            }
            
            public String getDetail() {
                return detail;
            }
            
            public void setDetail(String detail) {
                this.detail = detail;
            }
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