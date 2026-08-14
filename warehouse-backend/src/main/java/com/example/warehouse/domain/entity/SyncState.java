package com.example.warehouse.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per RestaurantLink, keyed by its id directly - holds the "since" cursor for the next
 * incremental sync call to Morá's sales endpoint (see WarehouseSyncService).
 */
@Entity
@Table(name = "sync_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncState {

    @Id
    @Column(name = "restaurant_link_id")
    private UUID restaurantLinkId;

    @Column(name = "last_synced_at", nullable = false)
    private OffsetDateTime lastSyncedAt;
}
