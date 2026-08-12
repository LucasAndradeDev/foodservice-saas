package com.example.restaurant_saas.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePixChargeRequest {

    /** Percentage (0-100) of the post-discount total to add as service charge. Only honored for an
     * OWNER/MANAGER caller (see TabController#createPixCharge) - null/omitted means use the
     * restaurant's configured default, same as any non-privileged caller. */
    private BigDecimal serviceChargePercentage;
}
