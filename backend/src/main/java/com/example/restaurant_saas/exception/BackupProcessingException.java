package com.example.restaurant_saas.exception;

public class BackupProcessingException extends RuntimeException {

    public BackupProcessingException(String message) {
        super(message);
    }

    public BackupProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
