package com.example.restaurant_saas.exception;

/** Wraps any failure talking to Mercado Pago (network error, non-2xx response, unexpected payload shape). */
public class CardGatewayException extends RuntimeException {
    public CardGatewayException(String message) {
        super(message);
    }

    public CardGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
