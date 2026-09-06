package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCourierLocationRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90", message = "Latitude must be at least -90")
    @DecimalMax(value = "90", message = "Latitude must be at most 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180", message = "Longitude must be at least -180")
    @DecimalMax(value = "180", message = "Longitude must be at most 180")
    private Double longitude;
}
