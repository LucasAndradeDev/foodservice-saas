package com.example.restaurant_saas.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
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

    @Column(name = "auto_print_kitchen_tickets", nullable = false)
    @Builder.Default
    private Boolean autoPrintKitchenTickets = false;

    @Column(name = "kitchen_warning_threshold_minutes", nullable = false)
    @Builder.Default
    private Integer kitchenWarningThresholdMinutes = 10;

    @Column(name = "kitchen_critical_threshold_minutes", nullable = false)
    @Builder.Default
    private Integer kitchenCriticalThresholdMinutes = 20;

    @Column(name = "service_charge_enabled", nullable = false)
    @Builder.Default
    private Boolean serviceChargeEnabled = true;

    @Column(name = "service_charge_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal serviceChargePercentage = BigDecimal.valueOf(10.00);

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
