package com.example.warehouse.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A purchase is an append-only ledger entry - it has no update endpoint. Its items feed
 * ingredient stock as an "entry" the same way a sale is an "exit" (see PurchaseService).
 */
@Entity
@Table(name = "purchases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_link_id", nullable = false)
    private UUID restaurantLinkId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "purchased_at", nullable = false)
    private OffsetDateTime purchasedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
