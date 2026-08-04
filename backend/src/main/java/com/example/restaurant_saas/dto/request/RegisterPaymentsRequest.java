package com.example.restaurant_saas.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RegisterPaymentsRequest {

    @NotEmpty(message = "At least one payment is required")
    @Size(max = 50, message = "At most 50 payments per call")
    @Valid
    private List<PaymentEntryRequest> payments;

    /** Percentage (0-100) of the post-discount total to add as service charge. Only honored while the
     * tab's bill total isn't locked yet (i.e. on the first payment registered for this tab). Null/omitted
     * means none. */
    private BigDecimal serviceChargePercentage;
}
