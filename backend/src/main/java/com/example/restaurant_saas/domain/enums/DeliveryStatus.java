package com.example.restaurant_saas.domain.enums;

public enum DeliveryStatus {
    SEPARATING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    // A side-exit from SEPARATING or OUT_FOR_DELIVERY, not a step in the normal forward flow -
    // see DeliveryService#updateStatus for the transition rules.
    CANCELLED
}
