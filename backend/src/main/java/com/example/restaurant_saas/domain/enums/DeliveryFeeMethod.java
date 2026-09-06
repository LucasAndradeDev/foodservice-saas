package com.example.restaurant_saas.domain.enums;

// Which of the two DeliveryFeeResolver strategies actually priced a given order - recorded on
// DeliveryDetails purely for staff transparency (DeliveryDetailsResponse#distanceKm); the frozen
// deliveryFee itself is the source of truth regardless of how it was computed.
public enum DeliveryFeeMethod {
    DISTANCE,
    ZONE
}
