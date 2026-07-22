package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.UserRole;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    private UserRole role;

    private Boolean active;
}
