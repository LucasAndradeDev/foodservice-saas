package com.example.warehouse.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The "tenant" of Armazém Morá - one row per restaurant that's linked to a Morá account.
 * Created automatically on the first SSO handoff (see WarehouseSsoService), never through a
 * manual pairing screen.
 */
@Entity
@Table(name = "restaurant_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mora_restaurant_id", nullable = false, unique = true)
    private UUID moraRestaurantId;

    @Column(nullable = false, length = 100)
    private String name;

    // Raw integration API key - see the migration comment for why this isn't hashed here.
    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
