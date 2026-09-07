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
