package com.example.restaurant_saas.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateDeliveryOrderRequest {

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CreateOrderItemRequest> items;

    @NotBlank(message = "Customer name is required")
    @Size(max = 255, message = "Customer name is too long")
    private String customerName;

    // Required for delivery, unlike the optional WhatsApp field on dine-in self-orders - there's
    // no other way to reach the customer if the courier can't find the address.
    @NotBlank(message = "Customer phone is required")
    @Size(max = 20, message = "Phone number is too long")
    @Pattern(regexp = "^(?=(?:\\D*\\d){10,15}\\D*$)[0-9+()\\-\\s]*$", message = "Invalid phone number")
    private String customerPhone;

    @NotBlank(message = "Street is required")
    @Size(max = 255, message = "Street is too long")
    private String street;

    @NotBlank(message = "Number is required")
    @Size(max = 20, message = "Number is too long")
    private String number;

    @Size(max = 255, message = "Complement is too long")
    private String complement;

    @NotBlank(message = "Neighborhood is required")
    @Size(max = 100, message = "Neighborhood is too long")
    private String neighborhood;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City is too long")
    private String city;

    @Size(max = 10, message = "Zip code is too long")
    private String zipCode;

    @Size(max = 255, message = "Reference point is too long")
    private String referencePoint;
}
