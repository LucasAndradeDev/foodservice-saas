package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCategoryRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    @Size(max = 500, message = "Banner image URL must be at most 500 characters long")
    private String bannerImageUrl;
}
