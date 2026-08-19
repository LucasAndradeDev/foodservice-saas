package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class DeliveryDetailsResponse {

    private UUID id;
    private UUID tabId;
    private DeliveryStatus status;

    // False while any item on the tab is still PENDING/PREPARING - gates the SEPARATING ->
    // OUT_FOR_DELIVERY transition, see DeliveryService.isKitchenReady.
    private boolean kitchenReady;

    // True once the tab's frozen total is fully paid (TabStatus.CLOSED) - the other half of the
    // SEPARATING -> OUT_FOR_DELIVERY gate (task 29.1): no cash-on-delivery in v1, so this must be
    // true before the order can leave the kitchen.
    private boolean paid;

    private String customerName;
    private String customerPhone;

    // Lets the tracking page offer "order again" / "message the restaurant" CTAs without the
    // customer needing to already have the restaurant's own menu link handy - restaurantPhone is
    // nullable (optional field on Restaurant, often left blank), so the frontend hides that CTA
    // when it's absent rather than linking to nothing.
    private String restaurantSlug;
    private String restaurantName;
    private String restaurantPhone;

    private String street;
    private String number;
    private String complement;
    private String neighborhood;
    private String city;
    private String zipCode;
    private String referencePoint;

    private BigDecimal deliveryFee;
    private List<DeliveryItemResponse> items;
    // The tab's own frozen total (items + service charge + deliveryFee) - same value staff sees,
    // not recomputed here, so this can never drift from what payment actually settles.
    private BigDecimal billTotal;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
