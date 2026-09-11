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

    // Free-text fallback, kept for restaurants that registered before the form switched to the
    // structured fields below (2026-09-11) - no longer sent by the frontend, but still accepted.
    private String address;

    // Structured address, same shape as UpdateRestaurantRequest's (task 26.5 follow-up) - lets a
    // brand new restaurant geocode correctly from the start instead of the owner having to re-enter
    // it in Settings later just to unlock distance-based delivery pricing.
    @Size(max = 255, message = "Street must be at most 255 characters long")
    private String street;

    @Size(max = 20, message = "Number must be at most 20 characters long")
    private String number;

    @Size(max = 255, message = "Complement must be at most 255 characters long")
    private String complement;

    @Size(max = 100, message = "Neighborhood must be at most 100 characters long")
    private String neighborhood;

    @Size(max = 100, message = "City must be at most 100 characters long")
    private String city;

    @Size(max = 10, message = "Zip code must be at most 10 characters long")
    private String zipCode;

    // Owner data (OWNER)
    @NotBlank(message = "Owner name is required")
    private String ownerName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String ownerEmail;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String ownerPassword;

    private boolean termsAccepted;
}
