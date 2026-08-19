package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.TabStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TabRepository extends JpaRepository<Tab, UUID> {
    List<Tab> findByRestaurantId(UUID restaurantId);
    Optional<Tab> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tab t WHERE t.id = :id AND t.restaurant.id = :restaurantId")
    Optional<Tab> findByIdAndRestaurantIdForUpdate(@Param("id") UUID id, @Param("restaurantId") UUID restaurantId);

    @Query("SELECT t FROM Tab t JOIN t.tables rt WHERE rt.id = :tableId AND t.restaurant.id = :restaurantId AND t.status = 'OPEN'")
    Optional<Tab> findOpenTabByRestaurantIdAndTableId(@Param("restaurantId") UUID restaurantId, @Param("tableId") UUID tableId);

    @Query("SELECT DISTINCT t FROM Tab t JOIN t.tables rt " +
            "WHERE t.restaurant.id = :restaurantId AND t.closedAt IS NOT NULL " +
            "AND t.openedAt < :rangeEnd AND t.closedAt > :rangeStart")
    List<Tab> findClosedTabsWithTablesOverlappingRange(
            @Param("restaurantId") UUID restaurantId, @Param("rangeStart") OffsetDateTime rangeStart, @Param("rangeEnd") OffsetDateTime rangeEnd);

    // A column projection, not the managed Tab entity - deliberately bypasses Hibernate's
    // session-scoped identity cache, unlike findById. Needed by DeliveryService#getByAccessToken:
    // it may have just triggered a card charge re-verification that updated this same tab's status
    // in a separate, already-committed transaction, and an entity fetch here would still return the
    // stale pre-verification instance already sitting in this transaction's persistence context.
    @Query("SELECT t.status FROM Tab t WHERE t.id = :id")
    TabStatus findStatusById(@Param("id") UUID id);
}
