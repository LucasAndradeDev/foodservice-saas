package com.example.warehouse.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recipes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_link_id", nullable = false)
    private UUID restaurantLinkId;

    // No FK - Recipe lives in a different database than Morá's Product (see docs/ARMAZEM_MORA.md).
    @Column(name = "mora_product_id", nullable = false)
    private UUID moraProductId;

    // Snapshot taken when the recipe is saved - keeps showing a sensible name even if the
    // product is later renamed or deleted on the Morá side.
    @Column(name = "mora_product_name", nullable = false, length = 150)
    private String moraProductName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
