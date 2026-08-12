package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SavePixIntegrationRequest {

    @NotBlank(message = "AppID is required")
    private String appId;
}
