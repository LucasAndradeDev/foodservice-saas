package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

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

    // Structured address (task 26.5 follow-up) - additive alongside `address` above, see
    // Restaurant entity. All optional; owner can keep using free-text `address` indefinitely.
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

    @Size(max = 20, message = "CNPJ must be at most 20 characters long")
    private String cnpj;

    private Boolean autoPrintKitchenTickets;

    @Positive(message = "Warning threshold must be greater than zero")
    private Integer kitchenWarningThresholdMinutes;

    @Positive(message = "Critical threshold must be greater than zero")
    private Integer kitchenCriticalThresholdMinutes;

    @Positive(message = "Warning threshold must be greater than zero")
    private Integer tableForgottenWarningThresholdMinutes;

    @Positive(message = "Critical threshold must be greater than zero")
    private Integer tableForgottenCriticalThresholdMinutes;

    private Boolean serviceChargeEnabled;

    @DecimalMin(value = "0.0", message = "Service charge percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "Service charge percentage cannot exceed 100")
    private BigDecimal serviceChargePercentage;

    @Positive(message = "Reservation block-before minutes must be greater than zero")
    private Integer reservationBlockBeforeMinutes;

    @Positive(message = "Reservation block-after minutes must be greater than zero")
    private Integer reservationBlockAfterMinutes;

    // Both null = distance-based delivery pricing stays unconfigured (DeliveryFeeResolver falls
    // back to DeliveryZone). Owner sets these together in the "Entrega" settings tab.
    @DecimalMin(value = "0.0", message = "Delivery base fee cannot be negative")
    private BigDecimal deliveryBaseFee;

    @DecimalMin(value = "0.0", message = "Delivery price per km cannot be negative")
    private BigDecimal deliveryFeePerKm;
}
