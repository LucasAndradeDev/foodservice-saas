package com.example.restaurant_saas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Deliberately never echoes the AppID back, encrypted or not - the frontend only needs to know
// whether Pix payment is configured for this restaurant, to decide whether to show the toggle
// as "connected" or the input to paste a new key.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PixIntegrationStatusResponse {
    private boolean configured;
}
