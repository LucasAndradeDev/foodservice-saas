package com.example.warehouse.security;

import java.util.UUID;

public record HandoffClaims(UUID restaurantId, String restaurantName, String apiKey) {
}
