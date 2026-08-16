package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateDeliveryZoneRequest {

    @NotBlank(message = "Neighborhood is required")
    @Size(max = 100, message = "Neighborhood is too long")
    private String neighborhood;

    @NotNull(message = "Fee is required")
    @DecimalMin(value = "0", message = "Fee cannot be negative")
    private BigDecimal fee;
}
