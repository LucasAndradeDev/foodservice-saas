package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRestaurantRequest {

    @Size(max = 100, message = "Trade name must be at most 100 characters long")
    private String tradeName;

    @Size(max = 150, message = "Slug must be at most 150 characters long")
    @Pattern(regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug must contain only lowercase letters, numbers and hyphens")
    private String slug;

    @Size(max = 255, message = "Logo URL must be at most 255 characters long")
    private String logo;

    @PositiveOrZero(message = "Table count cannot be negative")
    private Integer tableCount;

    @Size(max = 20, message = "Phone must be at most 20 characters long")
    private String phone;

    @Size(max = 255, message = "Address must be at most 255 characters long")
    private String address;

    @Size(max = 20, message = "CNPJ must be at most 20 characters long")
    private String cnpj;

    private Boolean autoPrintKitchenTickets;

    @Positive(message = "Warning threshold must be greater than zero")
    private Integer kitchenWarningThresholdMinutes;

    @Positive(message = "Critical threshold must be greater than zero")
    private Integer kitchenCriticalThresholdMinutes;
}
