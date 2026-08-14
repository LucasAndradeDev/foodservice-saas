package com.example.restaurant_saas.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "warehouse_integrations")
@Filter(name = "tenantFilter", condition = "restaurant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    private UUID restaurantId;

    // SHA-256 hex digest of the raw integration API key handed to the Armazém Morá backend
    // during SSO handoff - never store the raw key here, only Armazém holds it (see
    // docs/ARMAZEM_MORA.md). Compared with MessageDigest.isEqual, same pattern as BackupController.
    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
