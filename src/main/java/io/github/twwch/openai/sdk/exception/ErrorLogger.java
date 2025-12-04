package io.github.twwch.openai.sdk.exception;

import org.slf4j.Logger;

/**
 * 统一的错误日志工具类
 * 提供统一的错误日志格式和异常创建方式
 */
public class ErrorLogger {

    /**
     * 记录错误日志并创建异常
     *
     * @param logger   日志对象
     * @param provider 服务提供商
     * @param model    模型ID
     * @param message  错误消息
     * @param cause    原始异常
     * @return OpenAIException
     */
    public static OpenAIException logAndCreateException(Logger logger, OpenAIException.Provider provider,
                                                        String model, String message, Throwable cause) {
        OpenAIException exception = OpenAIException.builder(message)
                .provider(provider)
                .model(model)
                .cause(cause)
                .build();

        logger.error("{}", exception.getFormattedMessage(), cause);
        return exception;
    }

    /**
     * 记录错误日志并创建异常（带状态码）
     */
    public static OpenAIException logAndCreateException(Logger logger, OpenAIException.Provider provider,
                                                        String model, String message, int statusCode, Throwable cause) {
        OpenAIException exception = OpenAIException.builder(message)
                .provider(provider)
                .model(model)
                .statusCode(statusCode)
                .cause(cause)
                .build();

        logger.error("{}", exception.getFormattedMessage(), cause);
        return exception;
    }

    /**
     * 记录错误日志并创建异常（不带 cause）
     */
    public static OpenAIException logAndCreateException(Logger logger, OpenAIException.Provider provider,
                                                        String model, String message) {
        OpenAIException exception = OpenAIException.builder(message)
                .provider(provider)
                .model(model)
                .build();

        logger.error("{}", exception.getFormattedMessage());
        return exception;
    }

    /**
     * 记录错误日志并创建异常（带原始错误信息）
     */
    public static OpenAIException logAndCreateException(Logger logger, OpenAIException.Provider provider,
                                                        String model, String message, String rawError, Throwable cause) {
        OpenAIException exception = OpenAIException.builder(message)
                .provider(provider)
                .model(model)
                .rawError(rawError)
                .cause(cause)
                .build();

        logger.error("{}", exception.getFormattedMessage(), cause);
        return exception;
    }

    /**
     * 仅记录警告日志（用于重试等场景）
     */
    public static void logWarn(Logger logger, OpenAIException.Provider provider,
                               String model, String message, Throwable cause) {
        String formattedMessage = formatMessage(provider, model, message, cause != null ? cause.getMessage() : null);
        if (cause != null) {
            logger.warn("{}", formattedMessage, cause);
        } else {
            logger.warn("{}", formattedMessage);
        }
    }

    /**
     * 仅记录警告日志（不带 cause）
     */
    public static void logWarn(Logger logger, OpenAIException.Provider provider,
                               String model, String message) {
        logWarn(logger, provider, model, message, null);
    }

    /**
     * 格式化错误消息
     * 格式: [Provider] message | model: xxx | rawError: xxx
     */
    public static String formatMessage(OpenAIException.Provider provider, String model, String message, String rawError) {
        StringBuilder sb = new StringBuilder();

        // Provider
        if (provider != null) {
            sb.append("[").append(provider.getDisplayName()).append("] ");
        }

        // 主消息
        sb.append(message);

        // 模型
        if (model != null && !model.isEmpty()) {
            sb.append(" | model: ").append(model);
        }

        // 原始错误
        if (rawError != null && !rawError.isEmpty() && !rawError.equals(message)) {
            sb.append(" | rawError: ").append(rawError);
        }

        return sb.toString();
    }

    /**
     * 创建异常（不记录日志）
     */
    public static OpenAIException createException(OpenAIException.Provider provider, String model,
                                                  String message, Throwable cause) {
        return OpenAIException.builder(message)
                .provider(provider)
                .model(model)
                .cause(cause)
                .build();
    }

    /**
     * 创建异常（不记录日志，带状态码）
     */
    public static OpenAIException createException(OpenAIException.Provider provider, String model,
                                                  String message, int statusCode, Throwable cause) {
        return OpenAIException.builder(message)
                .provider(provider)
                .model(model)
                .statusCode(statusCode)
                .cause(cause)
                .build();
    }
}
