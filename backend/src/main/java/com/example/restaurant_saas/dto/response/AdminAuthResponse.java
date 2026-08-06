package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAuthResponse {
    private String accessToken;
    private String tokenType;
}
