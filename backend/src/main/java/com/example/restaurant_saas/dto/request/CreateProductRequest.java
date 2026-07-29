package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateProductRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    @Size(max = 255, message = "Description must be at most 255 characters long")
    private String description;

    @Size(max = 500, message = "Image URL must be at most 500 characters long")
    private String imageUrl;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Cost price must not be negative")
    private BigDecimal costPrice;

    @NotNull(message = "Category is required")
    private UUID categoryId;

    private Boolean featured;
}
