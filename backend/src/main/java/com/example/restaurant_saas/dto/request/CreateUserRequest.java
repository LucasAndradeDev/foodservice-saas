package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.CourierVehicleType;
import com.example.restaurant_saas.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotNull(message = "User role is required")
    private UserRole role;

    // Only meaningful (and required, validated in UserService) when role = COURIER.
    @Size(max = 20, message = "Phone must be at most 20 characters long")
    private String phone;

    private CourierVehicleType vehicleType;

    @Size(max = 255, message = "Notes must be at most 255 characters long")
    private String notes;
}
