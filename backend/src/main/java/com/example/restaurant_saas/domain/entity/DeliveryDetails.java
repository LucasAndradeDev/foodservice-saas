package com.example.restaurant_saas.domain.entity;

import com.example.restaurant_saas.domain.enums.DeliveryFeeMethod;
import com.example.restaurant_saas.domain.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "delivery_details")
@Filter(name = "tenantFilter", condition = "restaurant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tab_id", nullable = false, unique = true)
    private Tab tab;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(nullable = false, length = 255)
    private String street;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(length = 255)
    private String complement;

    @Column(nullable = false, length = 100)
    private String neighborhood;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "reference_point", length = 255)
    private String referencePoint;

    // Computed server-side from DeliveryZone (task 26) and frozen once set - never accepted
    // from the client, see docs/DELIVERY.md security section.
    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    // Transparency only, not recomputed/trusted for anything - see deliveryFee above.
    @Column(name = "delivery_distance_km", precision = 6, scale = 2)
    private BigDecimal deliveryDistanceKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_fee_method", length = 20)
    private DeliveryFeeMethod deliveryFeeMethod;

    // Geocoded once at order creation (DeliveryFeeResolver already geocodes the address to price
    // the distance-based fee) and reused by DeliveryService's live ETA refresh instead of
    // re-geocoding on every poll. Null whenever the order priced via DeliveryZone instead (no
    // geocoding happened) - ETA is just never shown in that case, same as courier location.
    @Column(name = "customer_latitude")
    private Double customerLatitude;

    @Column(name = "customer_longitude")
    private Double customerLongitude;

    // Cached, throttled route-duration estimate (see DeliveryService#refreshEtaIfStale) - not
    // recomputed on every 4s status poll, only when eta_updated_at is stale (>1 min old).
    @Column(name = "eta_minutes")
    private Integer etaMinutes;

    @Column(name = "eta_updated_at")
    private OffsetDateTime etaUpdatedAt;

    // Unguessable link for the customer to check delivery status without an account (task 27),
    // same pattern as Reservation.accessToken - generated as a random UUID by the app, never
    // sequential.
    @Column(name = "access_token", nullable = false, unique = true, length = 36)
    private String accessToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    // Nullable - most delivery orders never get one until task 27.1's SEPARATING stage, and it
    // can be cleared (SET NULL) if the courier account is later deactivated. A User with
    // role == COURIER, not a separate entity - couriers log in through the same account model
    // as any other staff member (task 28 redesign, 2026-08-31).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id")
    private User courier;

    // Set once, the moment status moves to OUT_FOR_DELIVERY/DELIVERED (DeliveryService#updateStatus)
    // - unlike updatedAt, never touched by anything else (e.g. the throttled ETA background
    // refresh), so it's the only reliable source for "how long has this been out" or "when was
    // this delivered" (courier's elapsed-time card and same-day history).
    @Column(name = "out_for_delivery_at")
    private OffsetDateTime outForDeliveryAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // Optimistic lock (finding #9, 2026-09-07 review) - a concurrent staff write (assign courier,
    // status change) racing DeliveryEtaService's throttled background refresh used to silently
    // last-write-wins. DeliveryEtaService already wraps its save in a best-effort catch
    // (DeliveryService#refreshEtaBestEffort), so a lost race there just logs and self-corrects on
    // the next poll instead of failing loudly.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
