package com.example.restaurant_saas.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, length = 20)
    private String cnpj;

    @Column(length = 20)
    private String phone;

    private String address;

    // Structured address (task 26.5 follow-up), same shape as DeliveryDetails' customer address -
    // additive alongside `address` above, which stays as the display/backfill fallback for
    // restaurants that haven't re-entered their address through these fields yet.
    @Column(length = 255)
    private String street;

    @Column(length = 20)
    private String number;

    @Column(length = 255)
    private String complement;

    @Column(length = 100)
    private String neighborhood;

    @Column(length = 100)
    private String city;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "trade_name", length = 100)
    private String tradeName;

    @Column(unique = true, length = 150)
    private String slug;

    private String logo;

    @Column(name = "table_count")
    @Builder.Default
    private Integer tableCount = 0;

    @Builder.Default
    private Boolean active = true;

    @Column(name = "payment_due_date")
    private LocalDate paymentDueDate;

    @Column(name = "auto_print_kitchen_tickets", nullable = false)
    @Builder.Default
    private Boolean autoPrintKitchenTickets = false;

    @Column(name = "kitchen_warning_threshold_minutes", nullable = false)
    @Builder.Default
    private Integer kitchenWarningThresholdMinutes = 10;

    @Column(name = "kitchen_critical_threshold_minutes", nullable = false)
    @Builder.Default
    private Integer kitchenCriticalThresholdMinutes = 20;

    @Column(name = "table_forgotten_warning_threshold_minutes", nullable = false)
    @Builder.Default
    private Integer tableForgottenWarningThresholdMinutes = 30;

    @Column(name = "table_forgotten_critical_threshold_minutes", nullable = false)
    @Builder.Default
    private Integer tableForgottenCriticalThresholdMinutes = 60;

    @Column(name = "service_charge_enabled", nullable = false)
    @Builder.Default
    private Boolean serviceChargeEnabled = true;

    @Column(name = "service_charge_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal serviceChargePercentage = BigDecimal.valueOf(10.00);

    @Column(name = "reservation_block_before_minutes", nullable = false)
    @Builder.Default
    private Integer reservationBlockBeforeMinutes = 30;

    @Column(name = "reservation_block_after_minutes", nullable = false)
    @Builder.Default
    private Integer reservationBlockAfterMinutes = 30;

    // Geocoded from `address` above whenever it's saved (RestaurantService#updateMyRestaurant) -
    // null until a successful geocode, or after `address` changes and the new one fails to
    // geocode (never left pointing at a stale address). Both null = distance-based delivery fee
    // is unavailable, falls back to DeliveryZone.
    private Double latitude;

    private Double longitude;

    // Both null = distance mode not configured; DeliveryFeeResolver falls back to DeliveryZone.
    @Column(name = "delivery_base_fee", precision = 10, scale = 2)
    private BigDecimal deliveryBaseFee;

    @Column(name = "delivery_fee_per_km", precision = 10, scale = 2)
    private BigDecimal deliveryFeePerKm;

    // Null = no cap (default, backward compatible) - set to reject/fall back to DeliveryZone for
    // any address farther than this from the restaurant. Only applies to the DISTANCE method.
    @Column(name = "max_delivery_distance_km", precision = 6, scale = 2)
    private BigDecimal maxDeliveryDistanceKm;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** {@code tradeName} ("nome fantasia") when set, falling back to the legal {@code name}
     * otherwise - used everywhere a restaurant's name is shown to a customer (menu, delivery
     * tracking, order tickets, feedback page). Checks blank, not just null: {@code tradeName} can
     * be an empty string since Configurações started letting staff clear it back out. */
    public String getDisplayName() {
        return tradeName != null && !tradeName.isBlank() ? tradeName : name;
    }
}
