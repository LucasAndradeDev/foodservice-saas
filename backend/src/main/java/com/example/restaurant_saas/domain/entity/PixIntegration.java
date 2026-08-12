package com.example.restaurant_saas.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pix_integrations")
@Filter(name = "tenantFilter", condition = "restaurant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PixIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    private UUID restaurantId;

    // The restaurant's Woovi AppID, encrypted with CredentialEncryptionService (AES/GCM) - unlike
    // WarehouseIntegration.apiKeyHash, this value must be recoverable: it's sent back to Woovi's
    // API as the Authorization header on every charge we create.
    @Column(name = "api_key_encrypted", nullable = false, columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
