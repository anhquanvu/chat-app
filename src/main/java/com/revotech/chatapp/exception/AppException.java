package com.revotech.chatapp.exception;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public AppException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
        this.errorCode = "GENERAL_ERROR";
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
        this.errorCode = "INTERNAL_ERROR";
    }

    public AppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.errorCode = "CUSTOM_ERROR";
    }

    public AppException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public AppException(String message, HttpStatus status, String errorCode, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // Static factory methods for common exceptions
    public static AppException notFound(String message) {
        return new AppException(message, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public static AppException unauthorized(String message) {
        return new AppException(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    public static AppException forbidden(String message) {
        return new AppException(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    public static AppException badRequest(String message) {
        return new AppException(message, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    public static AppException conflict(String message) {
        return new AppException(message, HttpStatus.CONFLICT, "CONFLICT");
    }

    public static AppException internalServerError(String message) {
        return new AppException(message, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR");
    }

    public static AppException validationError(String message) {
        return new AppException(message, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }
}
