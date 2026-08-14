package com.example.restaurant_saas.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RegisterPaymentsRequest {

    /** May be empty when the tab's bill total is zero (e.g. every item was cancelled/comped) —
     * there's nothing to collect, but the tab still needs to close and free its tables. */
    @NotNull(message = "Payments list is required")
    @Size(max = 50, message = "At most 50 payments per call")
    @Valid
    private List<PaymentEntryRequest> payments;

    /** Percentage (0-100) of the post-discount total to add as service charge. Only honored while the
     * tab's bill total isn't locked yet (i.e. on the first payment registered for this tab), and only
     * for an OWNER/MANAGER caller. Null/omitted means "no opinion" - falls back to the restaurant's
     * configured default. Send 0 explicitly to waive it on purpose. */
    private BigDecimal serviceChargePercentage;
}
