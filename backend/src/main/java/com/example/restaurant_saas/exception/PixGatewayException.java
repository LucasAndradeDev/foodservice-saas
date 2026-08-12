package com.example.restaurant_saas.exception;

/** Wraps any failure talking to Woovi (network error, non-2xx response, unexpected payload shape). */
public class PixGatewayException extends RuntimeException {
    public PixGatewayException(String message) {
        super(message);
    }

    public PixGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
