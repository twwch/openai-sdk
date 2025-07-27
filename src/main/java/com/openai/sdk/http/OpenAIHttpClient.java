package com.openai.sdk.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.sdk.AzureOpenAIConfig;
import com.openai.sdk.OpenAIConfig;
import com.openai.sdk.exception.OpenAIException;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI HTTP客户端
 */
public class OpenAIHttpClient {
    private final OkHttpClient client;
    private final OpenAIConfig config;
    private final ObjectMapper objectMapper;

    public OpenAIHttpClient(OpenAIConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.getTimeout(), TimeUnit.SECONDS)
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
                .build());
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
                    .build());
        } catch (JsonProcessingException e) {
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
                .build());
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
     * @return 响应体
     * @throws OpenAIException 如果请求失败
     */
    private String execute(Request request) throws OpenAIException {
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
                handleErrorResponse(response.code(), responseBody);
            }
            
            return responseBody;
        } catch (IOException e) {
            throw new OpenAIException("HTTP请求执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理错误响应
     *
     * @param statusCode  HTTP状态码
     * @param responseBody 响应体
     * @throws OpenAIException 包含错误详情的异常
     */
    private void handleErrorResponse(int statusCode, String responseBody) throws OpenAIException {
        String message = "请求失败，状态码: " + statusCode;
        String errorType = null;
        String errorCode = null;

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
        }

        throw new OpenAIException(message, statusCode, errorType, errorCode);
    }
}