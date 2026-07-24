package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class OpenTabRequest {

    @NotNull(message = "tableIds is required")
    private List<UUID> tableIds;
}
