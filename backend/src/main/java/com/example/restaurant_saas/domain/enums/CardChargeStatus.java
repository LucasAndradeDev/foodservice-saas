package com.example.restaurant_saas.domain.enums;

public enum CardChargeStatus {
    PENDING,
    PAID,
    // Unlike PixChargeStatus, a card charge can genuinely fail synchronously (a Pix charge only
    // ever expires or gets cancelled, never "declines").
    DECLINED,
    EXPIRED,
    CANCELLED
}
