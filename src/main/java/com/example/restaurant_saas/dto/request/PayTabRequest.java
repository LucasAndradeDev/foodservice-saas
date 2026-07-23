package com.example.restaurant_saas.dto.request;

import com.example.restaurant_saas.domain.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayTabRequest {

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Paid amount is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Paid amount must not be negative")
    private BigDecimal paidAmount;
}
