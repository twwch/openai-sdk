package com.openai.sdk.exception;

/**
 * OpenAI API异常类
 */
public class OpenAIException extends RuntimeException {
    private int statusCode;
    private String errorType;
    private String errorCode;

    public OpenAIException(String message) {
        super(message);
    }

    public OpenAIException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenAIException(String message, int statusCode, String errorType, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
        this.errorCode = errorCode;
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

    @Override
    public String toString() {
        return "OpenAIException{" +
                "message='" + getMessage() + '\'' +
                ", statusCode=" + statusCode +
                ", errorType='" + errorType + '\'' +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}