package io.github.twwch.openai.sdk.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.twwch.openai.sdk.AzureOpenAIConfig;
import io.github.twwch.openai.sdk.OpenAIConfig;
import io.github.twwch.openai.sdk.exception.OpenAIException;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI HTTP客户端
 */
public class OpenAIHttpClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIHttpClient.class);
    
    private final OkHttpClient client;
    private final OpenAIConfig config;
    private final ObjectMapper objectMapper;

    public OpenAIHttpClient(OpenAIConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        
        // 创建自定义的线程池，使用守护线程
        java.util.concurrent.ThreadFactory threadFactory = new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true); // 设置为守护线程
                thread.setName("OkHttp-" + thread.getId());
                return thread;
            }
        };
        
        // 为流式请求创建一个带有较短保持时间的OkHttpClient
        this.client = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES)) // 增加连接池大小，支持更多并发
                .dispatcher(new Dispatcher(java.util.concurrent.Executors.newCachedThreadPool(threadFactory)))
                .build();
    }

    /**
     * 执行GET请求
     *
     * @param endpoint API端点
     * @return 响应体
     * @throws OpenAIException 如果请求失败
     */
    public String get(String endpoint) throws OpenAIException {
        String url = buildUrl(endpoint);
        return execute(new Request.Builder()
                .url(url)
                .get()
                .build(), null);
    }

    /**
     * 执行POST请求
     *
     * @param endpoint API端点
     * @param body     请求体
     * @return 响应体
     * @throws OpenAIException 如果请求失败
     */
    public String post(String endpoint, Object body) throws OpenAIException {
        try {
            String url = buildUrl(endpoint);
            String jsonBody = objectMapper.writeValueAsString(body);
            RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            return execute(new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build(), jsonBody);
        } catch (JsonProcessingException e) {
            logger.error("序列化请求体失败: {}", endpoint, e);
            throw new OpenAIException("无法序列化请求体", e);
        }
    }

    /**
     * 执行DELETE请求
     *
     * @param endpoint API端点
     * @return 响应体
     * @throws OpenAIException 如果请求失败
     */
    public String delete(String endpoint) throws OpenAIException {
        String url = buildUrl(endpoint);
        return execute(new Request.Builder()
                .url(url)
                .delete()
                .build(), null);
    }
    
    /**
     * 构建请求URL
     *
     * @param endpoint API端点
     * @return 完整URL
     */
    private String buildUrl(String endpoint) {
        if (config.isAzure()) {
            AzureOpenAIConfig azureConfig = (AzureOpenAIConfig) config;
            
            // Azure OpenAI的URL格式不同
            if (endpoint.startsWith("/chat/completions")) {
                return config.getBaseUrl() + "/openai/deployments/" + azureConfig.getDeploymentId() + 
                       "/chat/completions?api-version=" + azureConfig.getApiVersion();
            } else if (endpoint.startsWith("/models")) {
                if (endpoint.equals("/models")) {
                    return config.getBaseUrl() + "/openai/models?api-version=" + azureConfig.getApiVersion();
                } else {
                    String modelId = endpoint.substring("/models/".length());
                    return config.getBaseUrl() + "/openai/models/" + modelId + "?api-version=" + azureConfig.getApiVersion();
                }
            }
            
            // 其他端点
            return config.getBaseUrl() + endpoint + "?api-version=" + azureConfig.getApiVersion();
        } else {
            // 标准OpenAI API
            return config.getBaseUrl() + endpoint;
        }
    }

    /**
     * 执行HTTP请求
     *
     * @param request HTTP请求
     * @param requestBody 请求体内容（用于错误日志）
     * @return 响应体
     * @throws OpenAIException 如果请求失败
     */
    private String execute(Request request, String requestBody) throws OpenAIException {
        // 构建带有认证头的请求
        Request.Builder requestBuilder = request.newBuilder()
                .addHeader("Content-Type", "application/json");
        
        // 根据配置类型添加不同的认证头
        if (config.isAzure()) {
            // Azure OpenAI使用api-key头
            requestBuilder.addHeader("api-key", config.getApiKey());
        } else {
            // 标准OpenAI使用Bearer认证
            requestBuilder.addHeader("Authorization", "Bearer " + config.getApiKey());
            
            // 如果提供了组织ID，添加相应的头部
            if (config.getOrganization() != null && !config.getOrganization().isEmpty()) {
                requestBuilder.addHeader("OpenAI-Organization", config.getOrganization());
            }
        }
        
        request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                handleErrorResponse(response.code(), responseBody, request.url().toString(), requestBody);
            }
            
            return responseBody;
        } catch (IOException e) {
            logger.error("HTTP请求执行失败 - URL: {}, 错误: {}", request.url(), e.getMessage(), e);
            throw new OpenAIException("HTTP请求执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理错误响应
     *
     * @param statusCode  HTTP状态码
     * @param responseBody 响应体
     * @param url 请求URL
     * @param requestBody 请求体
     * @throws OpenAIException 包含错误详情的异常
     */
    private void handleErrorResponse(int statusCode, String responseBody, String url, String requestBody) throws OpenAIException {
        String message = "请求失败，状态码: " + statusCode;
        String errorType = null;
        String errorCode = null;

        // 记录详细的错误信息
        logger.error("OpenAI API 错误 - 状态码: {}, URL: {}", statusCode, url);
        if (requestBody != null) {
            logger.debug("请求参数: {}", requestBody);
        }
        logger.error("错误响应: {}", responseBody);

        try {
            JsonNode errorJson = objectMapper.readTree(responseBody);
            if (errorJson.has("error")) {
                JsonNode error = errorJson.get("error");
                if (error.has("message")) {
                    message = error.get("message").asText();
                }
                if (error.has("type")) {
                    errorType = error.get("type").asText();
                }
                if (error.has("code")) {
                    errorCode = error.get("code").asText();
                }
            }
        } catch (Exception e) {
            // 如果解析失败，使用默认错误消息
            logger.debug("解析错误响应失败: {}", responseBody, e);
        }

        // 如果是400错误且涉及tools，添加特殊提示
        if (statusCode == 400 && requestBody != null && requestBody.contains("\"tools\"")) {
            message += " (可能是tools参数格式错误，请检查控制台输出的请求参数)";
        }

        throw new OpenAIException(message, statusCode, errorType, errorCode);
    }

    /**
     * 执行流式POST请求
     *
     * @param endpoint API端点
     * @param body     请求体
     * @param listener 事件监听器
     * @return EventSource 对象，用于关闭连接
     * @throws OpenAIException 如果请求失败
     */
    public EventSource postStream(String endpoint, Object body, EventSourceListener listener) throws OpenAIException {
        try {
            String url = buildUrl(endpoint);
            String jsonBody = objectMapper.writeValueAsString(body);
            RequestBody requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("Cache-Control", "no-cache");
            
            // 根据配置类型添加不同的认证头
            if (config.isAzure()) {
                // Azure OpenAI使用api-key头
                requestBuilder.addHeader("api-key", config.getApiKey());
            } else {
                // 标准OpenAI使用Bearer认证
                requestBuilder.addHeader("Authorization", "Bearer " + config.getApiKey());
                
                // 如果提供了组织ID，添加相应的头部
                if (config.getOrganization() != null && !config.getOrganization().isEmpty()) {
                    requestBuilder.addHeader("OpenAI-Organization", config.getOrganization());
                }
            }
            
            Request request = requestBuilder.build();
            
            // 创建EventSource
            EventSource.Factory factory = EventSources.createFactory(client);
            return factory.newEventSource(request, listener);
            
        } catch (JsonProcessingException e) {
            logger.error("序列化流式请求体失败: {}", endpoint, e);
            throw new OpenAIException("无法序列化请求体", e);
        }
    }
}