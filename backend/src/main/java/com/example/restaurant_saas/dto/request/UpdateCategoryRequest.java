package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCategoryRequest {

    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    private Boolean active;
}
