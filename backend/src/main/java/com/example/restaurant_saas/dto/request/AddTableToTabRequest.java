package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddTableToTabRequest {

    @NotNull(message = "Table id is required")
    private UUID tableId;
}
