package io.github.twwch.openai.sdk.exception;

/**
 * OpenAI API异常类
 * 统一的错误格式，包含模型信息和原始错误
 */
public class OpenAIException extends RuntimeException {
    /**
     * 服务提供商类型
     */
    public enum Provider {
        OPENAI("OpenAI"),
        BEDROCK("Bedrock"),
        GEMINI("Gemini"),
        UNKNOWN("Unknown");

        private final String displayName;

        Provider(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private Provider provider;
    private String model;
    private int statusCode;
    private String errorType;
    private String errorCode;
    private String rawError;

    public OpenAIException(String message) {
        super(message);
    }

    public OpenAIException(String message, Throwable cause) {
        super(message, cause);
        // 尝试从 cause 提取原始错误信息
        if (cause != null) {
            this.rawError = cause.getMessage();
        }
    }

    public OpenAIException(String message, int statusCode, String errorType, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
        this.errorCode = errorCode;
    }

    // ========== Builder 模式 ==========

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String message) {
        return new Builder().message(message);
    }

    public static class Builder {
        private String message;
        private Throwable cause;
        private Provider provider;
        private String model;
        private int statusCode;
        private String errorType;
        private String errorCode;
        private String rawError;

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            if (cause != null && this.rawError == null) {
                this.rawError = cause.getMessage();
            }
            return this;
        }

        public Builder provider(Provider provider) {
            this.provider = provider;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder rawError(String rawError) {
            this.rawError = rawError;
            return this;
        }

        public OpenAIException build() {
            OpenAIException exception;
            if (cause != null) {
                exception = new OpenAIException(message, cause);
            } else {
                exception = new OpenAIException(message);
            }
            exception.provider = this.provider;
            exception.model = this.model;
            exception.statusCode = this.statusCode;
            exception.errorType = this.errorType;
            exception.errorCode = this.errorCode;
            exception.rawError = this.rawError;
            return exception;
        }
    }

    // ========== Getters ==========

    public Provider getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getRawError() {
        return rawError;
    }

    // ========== Setters (for backward compatibility) ==========

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setRawError(String rawError) {
        this.rawError = rawError;
    }

    /**
     * 获取格式化的错误消息（用于日志输出）
     * 格式: [Provider] message | model: xxx | rawError: xxx
     */
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();

        // Provider
        if (provider != null) {
            sb.append("[").append(provider.getDisplayName()).append("] ");
        }

        // 主消息
        sb.append(getMessage());

        // 模型
        if (model != null && !model.isEmpty()) {
            sb.append(" | model: ").append(model);
        }

        // 状态码
        if (statusCode > 0) {
            sb.append(" | statusCode: ").append(statusCode);
        }

        // 原始错误
        if (rawError != null && !rawError.isEmpty() && !rawError.equals(getMessage())) {
            sb.append(" | rawError: ").append(rawError);
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("OpenAIException{");
        sb.append("message='").append(getMessage()).append('\'');

        if (provider != null) {
            sb.append(", provider=").append(provider.getDisplayName());
        }
        if (model != null) {
            sb.append(", model='").append(model).append('\'');
        }
        if (statusCode > 0) {
            sb.append(", statusCode=").append(statusCode);
        }
        if (errorType != null) {
            sb.append(", errorType='").append(errorType).append('\'');
        }
        if (errorCode != null) {
            sb.append(", errorCode='").append(errorCode).append('\'');
        }
        if (rawError != null) {
            sb.append(", rawError='").append(rawError).append('\'');
        }

        sb.append('}');
        return sb.toString();
    }
}