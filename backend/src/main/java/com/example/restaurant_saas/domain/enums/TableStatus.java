package com.example.restaurant_saas.domain.enums;

public enum TableStatus {
    FREE,
    OCCUPIED,
    CLOSING,
    // Never persisted -- computed on read whenever a FREE table falls inside an active reservation's
    // block window (see TableService). Setting it directly through the API is rejected.
    RESERVED
}
