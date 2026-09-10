package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.CourierVehicleType;
import com.example.restaurant_saas.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Size(max = 100, message = "Name must be at most 100 characters long")
    private String name;

    @Email(message = "Invalid email format")
    private String email;

    private UserRole role;

    private Boolean active;

    // Only meaningful for a COURIER user - null left alone, see UserService#updateUser.
    @Size(max = 20, message = "Phone must be at most 20 characters long")
    private String phone;

    private CourierVehicleType vehicleType;

    @Size(max = 255, message = "Notes must be at most 255 characters long")
    private String notes;
}
