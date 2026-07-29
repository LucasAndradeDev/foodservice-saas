package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByTabIdAndRestaurantId(UUID tabId, UUID restaurantId);
    Optional<Order> findByIdAndRestaurantId(UUID id, UUID restaurantId);
    Optional<Order> findFirstByTabIdAndRestaurantIdOrderByCreatedAtDesc(UUID tabId, UUID restaurantId);
    boolean existsByTabId(UUID tabId);
    List<Order> findByTabIdAndMergedFromTabId(UUID tabId, UUID mergedFromTabId);

    @Query("SELECT o.createdAt FROM Order o WHERE o.restaurant.id = :restaurantId " +
            "AND o.createdAt >= :rangeStart AND o.createdAt < :rangeEnd")
    List<OffsetDateTime> findCreatedAtByRestaurantIdAndCreatedAtBetween(
            @Param("restaurantId") UUID restaurantId, @Param("rangeStart") OffsetDateTime rangeStart, @Param("rangeEnd") OffsetDateTime rangeEnd);
}
