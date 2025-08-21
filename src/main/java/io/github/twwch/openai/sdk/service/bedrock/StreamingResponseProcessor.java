package io.github.twwch.openai.sdk.service.bedrock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.twwch.openai.sdk.model.chat.ChatCompletionChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 流式响应处理器，处理跨PayloadPart的数据和大参数工具调用
 */
public class StreamingResponseProcessor {
    private static final Logger logger = LoggerFactory.getLogger(StreamingResponseProcessor.class);
    
    private final BedrockModelAdapter modelAdapter;
    private final ObjectMapper objectMapper;
    private final Consumer<ChatCompletionChunk> onChunk;
    private final Consumer<Throwable> onError;
    
    // 缓冲区，用于存储不完整的行
    private final StringBuilder lineBuffer = new StringBuilder();
    
    // 工具参数累积器，key是工具调用的index
    private final Map<Integer, StringBuilder> toolArgumentsAccumulator = new ConcurrentHashMap<>();
    
    // 统计信息
    private long totalBytesReceived = 0;
    private long startTime = System.currentTimeMillis();
    private int chunksProcessed = 0;
    
    public StreamingResponseProcessor(BedrockModelAdapter modelAdapter, 
                                     ObjectMapper objectMapper,
                                     Consumer<ChatCompletionChunk> onChunk,
                                     Consumer<Throwable> onError) {
        this.modelAdapter = modelAdapter;
        this.objectMapper = objectMapper;
        this.onChunk = onChunk;
        this.onError = onError;
    }
    
    /**
     * 处理接收到的数据块
     */
    public void processChunk(String chunk) {
        try {
            totalBytesReceived += chunk.length();
            chunksProcessed++;
            
            // 添加到缓冲区
            lineBuffer.append(chunk);
            
            // 尝试处理完整的行
            processCompleteLines();
            
            // 记录处理统计
            if (chunksProcessed % 10 == 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                logger.debug("流式处理统计 - 已处理chunks: {}, 总字节数: {}, 耗时: {}ms, 速率: {} bytes/s", 
                    chunksProcessed, totalBytesReceived, elapsed, 
                    elapsed > 0 ? (totalBytesReceived * 1000 / elapsed) : 0);
            }
            
        } catch (Exception e) {
            logger.error("处理流式数据块失败 - chunk大小: {} bytes", chunk.length(), e);
            if (onError != null) {
                onError.accept(e);
            }
        }
    }
    
    /**
     * 处理缓冲区中的完整行
     */
    private void processCompleteLines() {
        String buffer = lineBuffer.toString();
        String[] parts = buffer.split("\n", -1);
        
        // 最后一个部分可能是不完整的行
        for (int i = 0; i < parts.length - 1; i++) {
            String line = parts[i].trim();
            if (!line.isEmpty()) {
                processLine(line);
            }
        }
        
        // 保留最后一个可能不完整的部分
        lineBuffer.setLength(0);
        if (parts.length > 0) {
            lineBuffer.append(parts[parts.length - 1]);
        }
    }
    
    /**
     * 处理单行数据
     */
    private void processLine(String line) {
        try {
            long lineStartTime = System.nanoTime();
            
            // 使用适配器转换
            List<ChatCompletionChunk> chunks = modelAdapter.convertStreamChunk(line, objectMapper);
            
            // 处理工具参数累积
            for (ChatCompletionChunk chunk : chunks) {
                processToolArguments(chunk);
                
                if (onChunk != null) {
                    onChunk.accept(chunk);
                }
            }
            
            long processingTime = (System.nanoTime() - lineStartTime) / 1_000_000;
            if (processingTime > 100) {
                logger.warn("处理单行耗时过长: {}ms, 行长度: {} bytes", processingTime, line.length());
            }
            
        } catch (Exception e) {
            logger.error("处理流式响应行失败 - 行长度: {} bytes, 内容前100字符: {}", 
                line.length(), 
                line.length() > 100 ? line.substring(0, 100) + "..." : line,
                e);
            
            // 不要静默失败，通知调用方
            if (onError != null) {
                onError.accept(new Exception("解析流式响应失败: " + e.getMessage(), e));
            }
        }
    }
    
    /**
     * 处理工具参数的累积
     */
    private void processToolArguments(ChatCompletionChunk chunk) {
        if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
            return;
        }
        
        ChatCompletionChunk.Delta delta = chunk.getChoices().get(0).getDelta();
        if (delta == null || delta.getToolCalls() == null) {
            return;
        }
        
        for (var toolCall : delta.getToolCalls()) {
            if (toolCall.getFunction() != null && toolCall.getFunction().getArguments() != null) {
                int index = toolCall.getIndex() != null ? toolCall.getIndex() : 0;
                
                // 累积参数
                StringBuilder accumulator = toolArgumentsAccumulator.computeIfAbsent(index, k -> new StringBuilder());
                String arguments = toolCall.getFunction().getArguments();
                accumulator.append(arguments);
                
                // 更新工具调用的参数为累积值
                toolCall.getFunction().setArguments(accumulator.toString());
                
                // 记录大参数
                if (accumulator.length() > 10000) {
                    logger.info("工具调用参数较大 - index: {}, 当前大小: {} bytes", index, accumulator.length());
                }
            }
        }
    }
    
    /**
     * 完成处理，刷新缓冲区
     */
    public void complete() {
        // 处理缓冲区中剩余的数据
        if (lineBuffer.length() > 0) {
            String remaining = lineBuffer.toString().trim();
            if (!remaining.isEmpty()) {
                logger.warn("流式响应结束时仍有未处理的数据: {} bytes", remaining.length());
                processLine(remaining);
            }
        }
        
        // 记录最终统计
        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("流式处理完成 - 总chunks: {}, 总字节: {}, 总耗时: {}ms, 平均速率: {} bytes/s", 
            chunksProcessed, totalBytesReceived, totalTime,
            totalTime > 0 ? (totalBytesReceived * 1000 / totalTime) : 0);
        
        // 记录工具参数统计
        if (!toolArgumentsAccumulator.isEmpty()) {
            toolArgumentsAccumulator.forEach((index, args) -> {
                logger.info("工具调用[{}]参数大小: {} bytes", index, args.length());
            });
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        lineBuffer.setLength(0);
        toolArgumentsAccumulator.clear();
    }
}