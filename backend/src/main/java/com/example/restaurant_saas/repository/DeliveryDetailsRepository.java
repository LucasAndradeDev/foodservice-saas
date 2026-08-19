package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import com.example.restaurant_saas.domain.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface DeliveryDetailsRepository extends JpaRepository<DeliveryDetails, UUID> {

    Optional<DeliveryDetails> findByTab_Id(UUID tabId);

    Optional<DeliveryDetails> findByTab_IdAndRestaurantId(UUID tabId, UUID restaurantId);

    List<DeliveryDetails> findByRestaurantIdAndStatusNotOrderByCreatedAtAsc(UUID restaurantId, DeliveryStatus status);

    // One query per kitchen-queue request instead of one per item (OrderItemService#toKitchenResponse)
    // - the queue can have dozens of items, and this only needs to happen once per restaurant. Kept
    // as full entities (not just tab ids) so the kitchen queue can also show which delivery order an
    // item belongs to (customerName), not just that it's a delivery.
    List<DeliveryDetails> findByRestaurantId(UUID restaurantId);

    // A delivery order's tab becomes CLOSED the moment it's fully paid (task 29.1) - well before
    // the item status flow (PENDING -> ... -> DELIVERED) finishes, since payment happens at
    // submission, not on completion. Used to keep an unpaid delivery order's items out of the
    // kitchen queue entirely (2026-08-18 decision) - no food prepped for an order that might never
    // get paid (abandoned cart, cancelled charge).
    @Query("SELECT dd.tab.id FROM DeliveryDetails dd WHERE dd.restaurantId = :restaurantId AND dd.tab.status <> 'CLOSED'")
    Set<UUID> findUnpaidTabIdsByRestaurantId(@Param("restaurantId") UUID restaurantId);

    // Bypasses RLS through a SECURITY DEFINER function (see V67 migration), same pattern as
    // ReservationRepository#findByAccessTokenBypassingRls - the only lookup that can't know
    // restaurant_id up front, since discovering it is the whole point of the token.
    @Query(value = "SELECT * FROM delivery_details_by_access_token(:token)", nativeQuery = true)
    Optional<DeliveryDetails> findByAccessTokenBypassingRls(@Param("token") String token);
}
