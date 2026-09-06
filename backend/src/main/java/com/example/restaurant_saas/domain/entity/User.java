package com.example.restaurant_saas.domain.entity;

import com.example.restaurant_saas.domain.enums.CourierVehicleType;
import com.example.restaurant_saas.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Filter(name = "tenantFilter", condition = "restaurant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Builder.Default
    private Boolean active = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = true;

    @Column(name = "terms_accepted_at")
    private OffsetDateTime termsAcceptedAt;

    // The next three columns are only ever populated for role == COURIER - null for every other
    // role. Kept directly on User rather than a side table: a courier is a User first (same
    // login/invite flow as any other staff), these are just the extra fields that role needs.
    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 20)
    private CourierVehicleType vehicleType;

    @Column(length = 255)
    private String notes;

    // Latest known position only - no history/trail. Also courier-only; updated by the courier's
    // own browser while /my-deliveries is open (see DeliveryService#updateMyLocation).
    private Double latitude;

    private Double longitude;

    @Column(name = "location_updated_at")
    private OffsetDateTime locationUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
