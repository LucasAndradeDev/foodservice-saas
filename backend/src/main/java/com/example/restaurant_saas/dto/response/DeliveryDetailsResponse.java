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

    // Geocoded destination point (DeliveryDetails.customerLatitude/Longitude) - null whenever the
    // order was priced via DeliveryZone instead of by-distance (never geocoded). Not fuzzed like
    // courierLatitude/Longitude below: it's the customer's own address, already sent in plaintext
    // as street/number/neighborhood above.
    private Double customerLatitude;
    private Double customerLongitude;

    private BigDecimal deliveryFee;

    // Staff-facing transparency only, showing how deliveryFee was computed (task 26.5) - null for
    // orders priced by neighborhood (DeliveryFeeMethod.ZONE) or placed before this existed.
    private BigDecimal deliveryDistanceKm;

    // Null until a courier is assigned on the operation screen (task 28.3) - courierName is
    // resolved here so the frontend never needs a second lookup just to show who's carrying it.
    private UUID courierId;
    private String courierName;

    // Null unless the order is OUT_FOR_DELIVERY and the assigned courier has reported a position
    // recently (see DeliveryService#toResponse) - the frontend never has to reason about staleness
    // itself. Rounded to ~100-150m on the public/unauthenticated path (getByAccessToken) only;
    // exact everywhere else (staff, the courier's own view).
    private Double courierLatitude;
    private Double courierLongitude;

    // Live route-based ETA (docs/DELIVERY.md live courier tracking follow-up) - null under the
    // exact same conditions as courierLatitude/Longitude above (not OUT_FOR_DELIVERY, no fresh
    // courier position), plus whenever the customer's address was priced via DeliveryZone (never
    // geocoded, so there's no point to route from) or every routing provider was unavailable.
    // Refreshed at most once a minute server-side (DeliveryService#refreshEtaIfStale) - not tied
    // to how often the frontend polls.
    private Integer etaMinutes;

    private List<DeliveryItemResponse> items;
    // The tab's own frozen total (items + service charge + deliveryFee) - same value staff sees,
    // not recomputed here, so this can never drift from what payment actually settles.
    private BigDecimal billTotal;

    // Null until the corresponding status transition happens (DeliveryService#updateStatus) - back
    // the courier's elapsed-time display and same-day history, see DeliveryDetails javadoc for why
    // updatedAt can't be reused for this.
    private OffsetDateTime outForDeliveryAt;
    private OffsetDateTime deliveredAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
