package com.example.restaurant_saas.exception;

public class MenuImportProcessingException extends RuntimeException {

    public MenuImportProcessingException(String message) {
        super(message);
    }

    public MenuImportProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
