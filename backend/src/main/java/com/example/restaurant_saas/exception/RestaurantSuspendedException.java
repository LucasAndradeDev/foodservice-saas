package com.example.restaurant_saas.exception;

public class RestaurantSuspendedException extends RuntimeException {

    public RestaurantSuspendedException(String message) {
        super(message);
    }
}
