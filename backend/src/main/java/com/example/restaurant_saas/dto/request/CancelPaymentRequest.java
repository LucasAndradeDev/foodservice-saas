package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CancelPaymentRequest {

    @NotBlank(message = "Reason is required")
    @Size(max = 255, message = "Reason must be at most 255 characters")
    private String reason;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Paid amount is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Paid amount must not be negative")
    private BigDecimal paidAmount;

    /** Percentage (0-100) of the post-discount total to add as service charge. Null/omitted means none. */
    private BigDecimal serviceChargePercentage;
}
