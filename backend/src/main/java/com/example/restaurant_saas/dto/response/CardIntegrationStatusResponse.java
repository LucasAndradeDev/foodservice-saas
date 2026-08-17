package com.example.restaurant_saas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Deliberately never echoes the access token or webhook secret back - the frontend only needs to
// know whether card payment is configured for this restaurant, to decide whether to show the
// toggle as "connected" or the inputs to paste new credentials.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardIntegrationStatusResponse {
    private boolean configured;
}
