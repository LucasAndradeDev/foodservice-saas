package com.example.warehouse.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ingredients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_link_id", nullable = false)
    private UUID restaurantLinkId;

    @Column(nullable = false, length = 100)
    private String name;

    // Free-text unit of measure (kg, l, un, ...) rather than an enum - keeps this open to
    // whatever unit a restaurant actually uses without a migration every time a new one shows up.
    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "current_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal currentQuantity;

    @Column(name = "low_stock_threshold", precision = 12, scale = 3)
    private BigDecimal lowStockThreshold;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
