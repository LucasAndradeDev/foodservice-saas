package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class MergeTabRequest {

    @NotNull(message = "Source tab id is required")
    private UUID sourceTabId;
}
