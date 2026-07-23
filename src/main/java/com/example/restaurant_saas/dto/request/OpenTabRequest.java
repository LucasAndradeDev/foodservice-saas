package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class OpenTabRequest {

    @NotEmpty(message = "At least one table is required")
    private List<UUID> tableIds;
}
