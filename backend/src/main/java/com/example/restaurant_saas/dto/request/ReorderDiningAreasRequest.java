package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ReorderDiningAreasRequest {

    @NotEmpty(message = "orderedIds must not be empty")
    private List<UUID> orderedIds;
}
