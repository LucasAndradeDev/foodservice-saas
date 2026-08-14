package com.example.restaurant_saas.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePixChargeRequest {

    /** Percentage (0-100) of the post-discount total to add as service charge. Only honored for an
     * OWNER/MANAGER caller (see TabController#createPixCharge) - null/omitted means use the
     * restaurant's configured default, same as any non-privileged caller. */
    private BigDecimal serviceChargePercentage;

    /** How much this specific charge should be for. Null/omitted means "the tab's full remaining
     * balance not already covered by a registered payment or another still-PENDING Pix charge" -
     * the same single-charge-covers-everything behavior as before this field existed. A caller
     * splitting the bill passes an explicit partial amount here instead, one call per QR code
     * (see PixChargeService#createCharge for the validation against what's actually still owed). */
    private BigDecimal amount;
}
