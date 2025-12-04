package io.github.twwch.openai.sdk.service.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionRequest;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionResponse;
import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import io.github.twwch.openai.sdk.util.ImageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Claude模型适配器
 */
public class ClaudeModelAdapter implements BedrockModelAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ClaudeModelAdapter.class);

    /**
     * AWS Bedrock Claude 模型的 cache_control 块最大数量限制
     * 参考: https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html
     */
    private static final int MAX_CACHE_CONTROL_BLOCKS = 4;
    
    @Override
    public boolean supports(String modelId) {
        return modelId != null && modelId.contains("anthropic.claude");
    }
    
    @Override
    public String convertRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        ObjectNode bedrockRequest = objectMapper.createObjectNode();
        
        // 必须设置的参数
        bedrockRequest.put("anthropic_version", "bedrock-2023-05-31");
        
        // 添加 anthropic_beta 参数以支持细粒度工具流式传输
        ArrayNode betaFeatures = objectMapper.createArrayNode();
        betaFeatures.add("fine-grained-tool-streaming-2025-05-14");
        bedrockRequest.set("anthropic_beta", betaFeatures);
        
        // 转换消息格式
        List<ChatMessage> messages = request.getMessages();
        StringBuilder systemPrompt = new StringBuilder();
        ArrayNode bedrockMessages = objectMapper.createArrayNode();
        
        for (ChatMessage message : messages) {
            if ("system".equals(message.getRole())) {
                if (systemPrompt.length() > 0) {
                    systemPrompt.append("\n\n");
                }
                systemPrompt.append(message.getContentAsString());
            } else if ("tool".equals(message.getRole())) {
                // Bedrock不支持tool角色，需要转换为特定格式的user消息
                ObjectNode bedrockMessage = objectMapper.createObjectNode();
                bedrockMessage.put("role", "user");
                
                // 构建tool_result内容
                ArrayNode contentArray = objectMapper.createArrayNode();
                ObjectNode toolResult = objectMapper.createObjectNode();
                toolResult.put("type", "tool_result");
                toolResult.put("tool_use_id", message.getToolCallId());
                toolResult.put("content", message.getContentAsString());
                contentArray.add(toolResult);
                
                bedrockMessage.set("content", contentArray);
                bedrockMessages.add(bedrockMessage);
            } else {
                ObjectNode bedrockMessage = objectMapper.createObjectNode();
                bedrockMessage.put("role", convertRole(message.getRole()));
                
                // 检查是否包含工具调用
                if (message.getToolCalls() != null && message.getToolCalls().length > 0) {
                    // 需要构建复杂的content数组
                    ArrayNode contentArray = objectMapper.createArrayNode();
                    
                    // 添加文本内容
                    String textContent = message.getContentAsString();
                    if (textContent != null && !textContent.isEmpty()) {
                        ObjectNode textNode = objectMapper.createObjectNode();
                        textNode.put("type", "text");
                        textNode.put("text", textContent);
                        contentArray.add(textNode);
                    }
                    
                    // 添加工具调用
                    for (ChatMessage.ToolCall toolCall : message.getToolCalls()) {
                        ObjectNode toolUse = objectMapper.createObjectNode();
                        toolUse.put("type", "tool_use");
                        toolUse.put("id", toolCall.getId());
                        toolUse.put("name", toolCall.getFunction().getName());
                        
                        // 解析工具参数
                        if (toolCall.getFunction().getArguments() != null) {
                            try {
                                JsonNode args = objectMapper.readTree(toolCall.getFunction().getArguments());
                                toolUse.set("input", args);
                            } catch (Exception e) {
                                // 如果解析失败，使用空对象
                                toolUse.set("input", objectMapper.createObjectNode());
                            }
                        }
                        
                        contentArray.add(toolUse);
                    }
                    
                    bedrockMessage.set("content", contentArray);
                } else {
                    // 处理内容 - 可能是字符串或数组
                    Object content = message.getContent();
                    logger.debug("处理消息content: 类型={}, 是否为null={}",
                        content != null ? content.getClass().getName() : "null", content == null);

                    if (content instanceof String) {
                        bedrockMessage.put("content", (String) content);
                    } else if (content instanceof ChatMessage.ContentPart[]) {
                        // 转换ContentPart数组为Bedrock格式
                        ArrayNode contentArray = objectMapper.createArrayNode();
                        ChatMessage.ContentPart[] parts = (ChatMessage.ContentPart[]) content;
                        
                        // 首先收集所有需要下载的URL图片
                        List<String> urlsToDownload = new ArrayList<>();
                        for (ChatMessage.ContentPart part : parts) {
                            if ("image_url".equals(part.getType()) && part.getImageUrl() != null) {
                                String url = part.getImageUrl().getUrl();
                                if (!url.startsWith("data:image/")) {
                                    urlsToDownload.add(url);
                                }
                            }
                        }
                        
                        // 并发下载所有URL图片
                        Map<String, String> downloadedImages = new HashMap<>();
                        if (!urlsToDownload.isEmpty()) {
                            logger.info("Batch downloading {} images for Bedrock Claude", urlsToDownload.size());
                            downloadedImages = ImageUtils.downloadAndConvertBatch(urlsToDownload);
                        }
                        
                        // 处理所有内容部分
                        int successfulImages = 0;
                        int failedImages = 0;
                        for (ChatMessage.ContentPart part : parts) {
                            if ("text".equals(part.getType())) {
                                ObjectNode textNode = objectMapper.createObjectNode();
                                textNode.put("type", "text");
                                textNode.put("text", part.getText());

                                // 添加cache_control支持
                                if (part.getCacheControl() != null) {
                                    ObjectNode cacheControl = objectMapper.createObjectNode();
                                    cacheControl.put("type", part.getCacheControl().getType());
                                    textNode.set("cache_control", cacheControl);
                                }

                                contentArray.add(textNode);
                                logger.debug("添加文本ContentPart到contentArray: type={}, text长度={}, has_cache_control={}",
                                    part.getType(), part.getText() != null ? part.getText().length() : 0, part.getCacheControl() != null);
                            } else if ("image_url".equals(part.getType()) && part.getImageUrl() != null) {
                                String url = part.getImageUrl().getUrl();
                                ObjectNode imageNode = objectMapper.createObjectNode();
                                imageNode.put("type", "image");
                                ObjectNode sourceNode = objectMapper.createObjectNode();
                                
                                String base64Data = null;
                                if (url.startsWith("data:image/")) {
                                    // 已经是base64编码的图片，检查是否需要压缩
                                    // Bedrock限制是5MB（5242880字节），这是base64编码后的大小
                                    // 我们需要确保最终的base64字符串不超过5MB
                                    // 传入的参数应该是最终base64的目标大小，而不是原始图片大小
                                    base64Data = ImageUtils.compressBase64Image(url, 5 * 1024 * 1024 - 100 * 1024); // 留100KB余量，确保不超过5MB
                                    if (!base64Data.equals(url)) {
                                        logger.info("Compressed base64 image for Claude");
                                    }
                                } else {
                                    // 从downloadedImages中获取已下载的图片（已经在下载时压缩过了）
                                    base64Data = downloadedImages.get(url);
                                    if (base64Data == null) {
                                        logger.error("Failed to download image: {}. Image might be too large or network error occurred.", url);
                                        failedImages++;
                                        // 添加一个文本说明，告知图片下载失败
                                        ObjectNode errorTextNode = objectMapper.createObjectNode();
                                        errorTextNode.put("type", "text");
                                        errorTextNode.put("text", "[图片下载失败: " + url.substring(url.lastIndexOf('/') + 1) + "]");
                                        contentArray.add(errorTextNode);
                                        continue;
                                    }
                                }
                                
                                // 解析base64数据
                                String[] parts2 = base64Data.split(",", 2);
                                if (parts2.length == 2) {
                                    String mediaType = parts2[0].substring(5, parts2[0].indexOf(";"));
                                    sourceNode.put("type", "base64");
                                    sourceNode.put("media_type", mediaType);
                                    sourceNode.put("data", parts2[1]);
                                    imageNode.set("source", sourceNode);
                                    contentArray.add(imageNode);
                                    successfulImages++;
                                } else {
                                    logger.error("Failed to parse base64 data for image: {}", url);
                                    failedImages++;
                                }
                            }
                        }
                        
                        if (failedImages > 0) {
                            logger.warn("Successfully processed {} images, {} images failed to download or process", 
                                successfulImages, failedImages);
                        }
                        
                        bedrockMessage.set("content", contentArray);
                    } else if (content instanceof List) {
                        // 处理List类型(Jackson反序列化可能将数组变成List)
                        logger.warn("检测到content是List类型,尝试转换为ContentPart数组");
                        List<?> listContent = (List<?>) content;
                        ArrayNode contentArray = objectMapper.createArrayNode();

                        for (Object item : listContent) {
                            if (item instanceof ChatMessage.ContentPart) {
                                ChatMessage.ContentPart part = (ChatMessage.ContentPart) item;
                                if ("text".equals(part.getType())) {
                                    ObjectNode textNode = objectMapper.createObjectNode();
                                    textNode.put("type", "text");
                                    textNode.put("text", part.getText());
                                    if (part.getCacheControl() != null) {
                                        ObjectNode cacheControl = objectMapper.createObjectNode();
                                        cacheControl.put("type", part.getCacheControl().getType());
                                        textNode.set("cache_control", cacheControl);
                                    }
                                    contentArray.add(textNode);
                                } else if ("image_url".equals(part.getType()) && part.getImageUrl() != null) {
                                    // 处理图片类型
                                    ObjectNode imageNode = objectMapper.createObjectNode();
                                    imageNode.put("type", "image");
                                    // ... 图片处理逻辑可以简化或重用
                                    logger.warn("List中包含image类型,当前简化处理");
                                }
                            } else {
                                logger.warn("List中包含非ContentPart类型的对象: {}", item != null ? item.getClass().getName() : "null");
                            }
                        }

                        bedrockMessage.set("content", contentArray);
                    } else if (content != null) {
                        // 尝试将其他类型转换为JSON
                        logger.warn("content是未知类型,使用valueToTree序列化: {}", content.getClass().getName());
                        bedrockMessage.set("content", objectMapper.valueToTree(content));
                    }
                }
                
                bedrockMessages.add(bedrockMessage);
            }
        }
        
        // 构建请求 - 必须参数
        bedrockRequest.set("messages", bedrockMessages);
        
        // max_tokens是必须的
        if (request.getMaxTokens() != null) {
            bedrockRequest.put("max_tokens", request.getMaxTokens());
        } else {
            bedrockRequest.put("max_tokens", 4096); // Claude默认值
        }
        
        // 可选参数 - 只在非默认值时设置

        // system - 系统提示
        if (systemPrompt.length() > 0) {
            // 如果启用了system缓存,使用数组格式
            if (Boolean.TRUE.equals(request.getBedrockEnableSystemCache())) {
                ArrayNode systemArray = objectMapper.createArrayNode();
                ObjectNode systemBlock = objectMapper.createObjectNode();
                systemBlock.put("type", "text");
                systemBlock.put("text", systemPrompt.toString());

                // 添加cache_control
                ObjectNode cacheControl = objectMapper.createObjectNode();
                cacheControl.put("type", "ephemeral");
                systemBlock.set("cache_control", cacheControl);

                systemArray.add(systemBlock);
                bedrockRequest.set("system", systemArray);

                logger.debug("启用了system prompt缓存,system长度: {} 字符", systemPrompt.length());
            } else {
                // 标准的字符串格式
                bedrockRequest.put("system", systemPrompt.toString());
            }
        }
        
        // temperature - 范围 0-1，默认 1
        if (request.getTemperature() != null && request.getTemperature() >= 0 && request.getTemperature() <= 1) {
            bedrockRequest.put("temperature", request.getTemperature());
        }
        
        // top_p - 范围 0-1，默认 0.999
        if (request.getTopP() != null && request.getTopP() >= 0 && request.getTopP() <= 1) {
            bedrockRequest.put("top_p", request.getTopP());
        }
        
        // top_k - 范围 0-500，默认禁用
        // OpenAI请求中没有top_k，所以跳过
        
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            ArrayNode stopSequences = objectMapper.createArrayNode();
            for (String stop : request.getStop()) {
                stopSequences.add(stop);
            }
            bedrockRequest.set("stop_sequences", stopSequences);
        }
        
        // 转换工具定义
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            ArrayNode bedrockTools = objectMapper.createArrayNode();
            
            for (ChatCompletionRequest.Tool tool : request.getTools()) {
                if ("function".equals(tool.getType()) && tool.getFunction() != null) {

                    ObjectNode bedrockTool = objectMapper.createObjectNode();
                    ChatCompletionRequest.Function function = tool.getFunction();
                    
                    bedrockTool.put("name", function.getName());
                    bedrockTool.put("description", function.getDescription());
                    
                    // 转换参数schema
                    if (function.getParameters() != null) {
                        bedrockTool.set("input_schema", objectMapper.valueToTree(function.getParameters()));
                    }
                    bedrockTools.add(bedrockTool);
                }
            }
            
            if (bedrockTools.size() > 0) {
                bedrockRequest.set("tools", bedrockTools);
                logger.debug("添加了 {} 个工具到请求中", bedrockTools.size());
            }
        }
        
        // 设置tool_choice - 当有tools时必须设置
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            ObjectNode toolChoice = objectMapper.createObjectNode();
            
            if (request.getToolChoice() == null) {
                // 如果没有指定，默认为 auto
                toolChoice.put("type", "auto");
            } else if ("auto".equals(request.getToolChoice())) {
                toolChoice.put("type", "auto");
            } else if ("none".equals(request.getToolChoice())) {
                toolChoice.put("type", "none");
            } else if ("required".equals(request.getToolChoice())) {
                toolChoice.put("type", "any");
            } else if (request.getToolChoice() instanceof String) {
                // 字符串形式，假定是工具名称
                toolChoice.put("type", "tool");
                toolChoice.put("name", (String) request.getToolChoice());
            } else if (request.getToolChoice() instanceof Map) {
                // 处理对象形式的 tool_choice
                Map<String, Object> tcMap = (Map<String, Object>) request.getToolChoice();
                
                // OpenAI 格式: {"type": "function", "function": {"name": "my_function"}}
                if ("function".equals(tcMap.get("type")) && tcMap.containsKey("function")) {
                    Map<String, Object> functionMap = (Map<String, Object>) tcMap.get("function");
                    if (functionMap != null && functionMap.containsKey("name")) {
                        toolChoice.put("type", "tool");
                        toolChoice.put("name", (String) functionMap.get("name"));
                    }
                } 
                // Bedrock 格式: {"type": "tool", "name": "my_function"} 或 {"type": "auto"}
                else if (tcMap.containsKey("type")) {
                    String type = (String) tcMap.get("type");
                    if ("auto".equals(type) || "none".equals(type) || "any".equals(type)) {
                        toolChoice.put("type", type);
                    } else if ("tool".equals(type) && tcMap.containsKey("name")) {
                        toolChoice.put("type", "tool");
                        toolChoice.put("name", (String) tcMap.get("name"));
                    }
                }
            }
            
            bedrockRequest.set("tool_choice", toolChoice);
            try {
                logger.debug("设置 tool_choice: {}", objectMapper.writeValueAsString(toolChoice));
            } catch (Exception e) {
                logger.debug("设置 tool_choice: [无法序列化]");
            }
        }

        // 限制 cache_control 块的数量，AWS Bedrock Claude 最多允许 4 个
        limitCacheControlBlocks(bedrockRequest);

        return objectMapper.writeValueAsString(bedrockRequest);
    }
    
    @Override
    public String convertStreamRequest(ChatCompletionRequest request, ObjectMapper objectMapper) throws Exception {
        // Claude的流式请求格式与非流式相同
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
        
        // 提取内容和工具调用
        JsonNode contentNode = responseNode.get("content");
        StringBuilder textContent = new StringBuilder();
        List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
        
        if (contentNode != null && contentNode.isArray()) {
            for (JsonNode content : contentNode) {
                String type = content.has("type") ? content.get("type").asText() : "text";
                
                if ("text".equals(type) && content.has("text")) {
                    textContent.append(content.get("text").asText());
                } else if ("tool_use".equals(type)) {
                    // 转换工具调用
                    ChatMessage.ToolCall toolCall = new ChatMessage.ToolCall();
                    toolCall.setId(content.has("id") ? content.get("id").asText() : UUID.randomUUID().toString());
                    toolCall.setType("function");
                    
                    ChatMessage.ToolCall.Function function = new ChatMessage.ToolCall.Function();
                    function.setName(content.has("name") ? content.get("name").asText() : "");
                    
                    // 转换参数
                    if (content.has("input")) {
                        function.setArguments(objectMapper.writeValueAsString(content.get("input")));
                    }
                    
                    toolCall.setFunction(function);
                    toolCalls.add(toolCall);
                }
            }
        } else if (responseNode.has("completion")) {
            textContent.append(responseNode.get("completion").asText());
        }
        
        if (textContent.length() > 0) {
            message.setContent(textContent.toString());
        } else if (contentNode == null || (contentNode.isArray() && contentNode.size() == 0)) {
            // 处理空content的情况，可能是因为stop_sequence
            message.setContent("");
        }
        
        if (!toolCalls.isEmpty()) {
            message.setToolCalls(toolCalls.toArray(new ChatMessage.ToolCall[0]));
        }
        
        choice.setMessage(message);
        
        // 设置完成原因
        if (responseNode.has("stop_reason")) {
            String stopReason = responseNode.get("stop_reason").asText();
            choice.setFinishReason(convertFinishReason(stopReason));
        }
        
        choices.add(choice);
        response.setChoices(choices);
        
        // 设置使用情况 - 直接使用Jackson反序列化，自动处理字段映射
        if (responseNode.has("usage")) {
            JsonNode usageNode = responseNode.get("usage");

            // 打印原始usage JSON用于调试
            logger.info("原始Bedrock响应usage字段: {}", usageNode.toString());

            // 直接反序列化为Usage对象，Jackson会自动映射字段
            // input_tokens -> promptTokens (通过setInputTokens方法)
            // output_tokens -> completionTokens (通过setOutputTokens方法)
            // cache_read_input_tokens -> cacheReadInputTokens (直接映射)
            // cache_creation_input_tokens -> cacheCreationInputTokens (直接映射)
            ChatCompletionResponse.Usage usage = objectMapper.treeToValue(usageNode, ChatCompletionResponse.Usage.class);

            // 计算总token数
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());

            // 打印缓存统计信息
            if (usage.getCacheReadInputTokens() != null && usage.getCacheReadInputTokens() > 0) {
                logger.info("✅ 缓存命中! 从缓存读取了 {} tokens (节省约90%成本)", usage.getCacheReadInputTokens());
            }
            if (usage.getCacheCreationInputTokens() != null && usage.getCacheCreationInputTokens() > 0) {
                logger.info("⚠️  创建缓存: {} tokens 已写入缓存 (成本约为标准输入的125%)", usage.getCacheCreationInputTokens());
            }

            response.setUsage(usage);
        } else {
            // 如果没有usage字段,创建一个空的Usage对象
            ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage();
            response.setUsage(usage);
        }

        return response;
    }
    
    @Override
    public List<ChatCompletionChunk> convertStreamChunk(String chunk, ObjectMapper objectMapper) throws Exception {
        List<ChatCompletionChunk> chunks = new ArrayList<>();
        
        // Claude的流式响应格式
        String[] lines = chunk.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            try {
                JsonNode chunkNode = objectMapper.readTree(line);
                
                
                ChatCompletionChunk completionChunk = new ChatCompletionChunk();
                completionChunk.setId("chatcmpl-" + UUID.randomUUID().toString());
                completionChunk.setObject("chat.completion.chunk");
                completionChunk.setCreated(System.currentTimeMillis() / 1000);
                completionChunk.setModel("claude");
                
                List<ChatCompletionChunk.Choice> choices = new ArrayList<>();
                ChatCompletionChunk.Choice choice = new ChatCompletionChunk.Choice();
                choice.setIndex(0);
                
                ChatCompletionChunk.Delta delta = new ChatCompletionChunk.Delta();
                
                // 处理不同类型的事件
                if (chunkNode.has("type")) {
                    String type = chunkNode.get("type").asText();
                    
                    
                    if ("content_block_start".equals(type)) {
                        // 工具调用开始
                        JsonNode contentBlock = chunkNode.get("content_block");
                        if (contentBlock != null && "tool_use".equals(contentBlock.get("type").asText())) {
                            List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
                            ChatMessage.ToolCall toolCall = new ChatMessage.ToolCall();
                            toolCall.setId(contentBlock.get("id").asText());
                            toolCall.setType("function");
                            
                            ChatMessage.ToolCall.Function function = new ChatMessage.ToolCall.Function();
                            function.setName(contentBlock.get("name").asText());
                            function.setArguments(""); // 初始为空，后续会通过delta更新
                            
                            toolCall.setFunction(function);
                            toolCalls.add(toolCall);
                            delta.setToolCalls(toolCalls);
                        }
                    } else if ("content_block_delta".equals(type)) {
                        JsonNode deltaNode = chunkNode.get("delta");
                        if (deltaNode != null) {
                            if (deltaNode.has("text")) {
                                // 文本内容
                                delta.setContent(deltaNode.get("text").asText());
                            } else if (deltaNode.has("partial_json")) {
                                // 工具调用参数的增量更新
                                String partialJson = deltaNode.get("partial_json").asText();
                                if (chunkNode.has("index")) {
                                    // 创建工具调用增量
                                    List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
                                    ChatMessage.ToolCall toolCall = new ChatMessage.ToolCall();
                                    toolCall.setIndex(chunkNode.get("index").asInt());
                                    
                                    ChatMessage.ToolCall.Function function = new ChatMessage.ToolCall.Function();
                                    function.setArguments(partialJson);
                                    
                                    toolCall.setFunction(function);
                                    toolCalls.add(toolCall);
                                    delta.setToolCalls(toolCalls);
                                }
                            }
                        }
                    } else if ("content_block_stop".equals(type)) {
                        // 内容块结束事件
                    } else if ("message_start".equals(type)) {
                        delta.setRole("assistant");


                        // 检查 message 对象中的所有内容
                        if (chunkNode.has("message")) {
                            JsonNode messageNode = chunkNode.get("message");

                            // 检查是否有usage信息
                            if (messageNode.has("usage")) {
                                JsonNode usageNode = messageNode.get("usage");

                                // 打印原始流式usage JSON用于调试(message_start事件)
                                logger.info("原始Bedrock流式响应usage字段(message_start): {}", usageNode.toString());

                                // 直接反序列化为Usage对象，Jackson会自动映射所有字段
                                ChatCompletionResponse.Usage usage = objectMapper.treeToValue(usageNode, ChatCompletionResponse.Usage.class);

                                // 计算总token数
                                usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());

                                // 打印缓存统计信息
                                if (usage.getCacheReadInputTokens() != null && usage.getCacheReadInputTokens() > 0) {
                                    logger.info("流式响应(message_start)缓存命中! {} tokens", usage.getCacheReadInputTokens());
                                }
                                if (usage.getCacheCreationInputTokens() != null && usage.getCacheCreationInputTokens() > 0) {
                                    logger.info("流式响应(message_start)创建缓存: {} tokens", usage.getCacheCreationInputTokens());
                                }

                                completionChunk.setUsage(usage);
                            }
                            
                            // 检查是否有 content 数组（可能包含工具调用）
                            if (messageNode.has("content") && messageNode.get("content").isArray()) {
                                ArrayNode contentArray = (ArrayNode) messageNode.get("content");
                                List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
                                
                                for (JsonNode contentItem : contentArray) {
                                    if (contentItem.has("type") && "tool_use".equals(contentItem.get("type").asText())) {
                                        ChatMessage.ToolCall toolCall = new ChatMessage.ToolCall();
                                        toolCall.setId(contentItem.get("id").asText());
                                        toolCall.setType("function");
                                        
                                        ChatMessage.ToolCall.Function function = new ChatMessage.ToolCall.Function();
                                        function.setName(contentItem.get("name").asText());
                                        
                                        // 初始参数为空，后续通过 content_block_delta 更新
                                        function.setArguments("");
                                        
                                        toolCall.setFunction(function);
                                        toolCalls.add(toolCall);
                                    }
                                }
                                
                                if (!toolCalls.isEmpty()) {
                                    delta.setToolCalls(toolCalls);
                                }
                            }
                        }
                    } else if ("message_stop".equals(type)) {
                        choice.setFinishReason("stop");
                    } else if ("message_delta".equals(type)) {
                        // message_delta事件通常只包含output_tokens的增量
                        // input_tokens和cache信息已经在message_start中提供
                        if (chunkNode.has("usage")) {
                            JsonNode usageNode = chunkNode.get("usage");

                            // 打印原始流式usage JSON用于调试
                            logger.info("原始Bedrock流式响应usage字段(message_delta): {}", usageNode.toString());

                            // 直接反序列化为Usage对象，Jackson会自动映射所有字段
                            ChatCompletionResponse.Usage usage = objectMapper.treeToValue(usageNode, ChatCompletionResponse.Usage.class);

                            // 计算总token数
                            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());

                            // 打印缓存统计信息（如果message_delta中有的话，通常没有）
                            if (usage.getCacheReadInputTokens() != null && usage.getCacheReadInputTokens() > 0) {
                                logger.info("流式响应(message_delta)缓存命中! 从缓存读取了 {} tokens", usage.getCacheReadInputTokens());
                            }
                            if (usage.getCacheCreationInputTokens() != null && usage.getCacheCreationInputTokens() > 0) {
                                logger.info("流式响应(message_delta)创建缓存: {} tokens", usage.getCacheCreationInputTokens());
                            }

                            completionChunk.setUsage(usage);
                        }
                        
                        // 处理stop_reason等其他delta字段
                        if (chunkNode.has("delta")) {
                            JsonNode deltaNode = chunkNode.get("delta");
                            if (deltaNode.has("stop_reason")) {
                                String stopReason = deltaNode.get("stop_reason").asText();
                                choice.setFinishReason(convertFinishReason(stopReason));
                            }
                        }
                    }
                }
                
                choice.setDelta(delta);
                choices.add(choice);
                completionChunk.setChoices(choices);
                
                chunks.add(completionChunk);
                
            } catch (Exception e) {
                logger.debug("忽略无法解析的流式事件行: {}", line, e);
            }
        }
        
        return chunks;
    }
    
    private String convertRole(String openAIRole) {
        if ("system".equals(openAIRole)) {
            return "user"; // Claude将system消息作为第一个user消息
        }
        return openAIRole;
    }
    
    private String convertFinishReason(String claudeReason) {
        switch (claudeReason) {
            case "end_turn":
            case "stop_sequence":
                return "stop";
            case "max_tokens":
                return "length";
            default:
                return claudeReason;
        }
    }

    /**
     * 限制 cache_control 块的数量，AWS Bedrock Claude 最多允许 4 个
     * 如果超过限制，从后往前移除多余的 cache_control 块
     * 优先保留 system prompt 的缓存（如果有的话）
     *
     * @param bedrockRequest 请求节点
     */
    private void limitCacheControlBlocks(ObjectNode bedrockRequest) {
        List<ObjectNode> nodesWithCacheControl = new ArrayList<>();

        // 收集所有包含 cache_control 的节点
        collectNodesWithCacheControl(bedrockRequest, nodesWithCacheControl);

        int totalCount = nodesWithCacheControl.size();
        if (totalCount <= MAX_CACHE_CONTROL_BLOCKS) {
            if (totalCount > 0) {
                logger.debug("cache_control 块数量: {}, 未超过限制 ({})", totalCount, MAX_CACHE_CONTROL_BLOCKS);
            }
            return;
        }

        // 超过限制，需要移除多余的 cache_control
        int toRemove = totalCount - MAX_CACHE_CONTROL_BLOCKS;
        logger.warn("cache_control 块数量 ({}) 超过 AWS Bedrock 限制 ({})，将移除 {} 个",
                totalCount, MAX_CACHE_CONTROL_BLOCKS, toRemove);

        // 从后往前移除（保留前面的，通常 system prompt 在前面更重要）
        for (int i = totalCount - 1; i >= MAX_CACHE_CONTROL_BLOCKS; i--) {
            ObjectNode node = nodesWithCacheControl.get(i);
            node.remove("cache_control");
            logger.debug("移除了第 {} 个 cache_control 块", i + 1);
        }

        logger.info("已自动移除 {} 个 cache_control 块，保留前 {} 个", toRemove, MAX_CACHE_CONTROL_BLOCKS);
    }

    /**
     * 递归收集所有包含 cache_control 的 ObjectNode
     *
     * @param node   当前节点
     * @param result 结果列表
     */
    private void collectNodesWithCacheControl(JsonNode node, List<ObjectNode> result) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            if (objectNode.has("cache_control")) {
                result.add(objectNode);
            }
            // 递归处理所有子节点
            Iterator<JsonNode> elements = objectNode.elements();
            while (elements.hasNext()) {
                collectNodesWithCacheControl(elements.next(), result);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                collectNodesWithCacheControl(element, result);
            }
        }
    }
}