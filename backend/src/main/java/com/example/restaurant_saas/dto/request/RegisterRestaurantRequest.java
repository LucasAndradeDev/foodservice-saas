package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRestaurantRequest {

    // Restaurant data
    @NotBlank(message = "Restaurant name is required")
    private String restaurantName;

    private String cnpj;
    private String phone;
    private String address;

    // Owner data (OWNER)
    @NotBlank(message = "Owner name is required")
    private String ownerName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String ownerEmail;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String ownerPassword;

    private boolean termsAccepted;
}
