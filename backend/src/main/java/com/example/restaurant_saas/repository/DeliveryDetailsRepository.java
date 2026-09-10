package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import com.example.restaurant_saas.domain.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface DeliveryDetailsRepository extends JpaRepository<DeliveryDetails, UUID> {

    Optional<DeliveryDetails> findByTab_Id(UUID tabId);

    Optional<DeliveryDetails> findByTab_IdAndRestaurantId(UUID tabId, UUID restaurantId);

    // Excludes both terminal statuses (DELIVERED and CANCELLED) - the staff Delivery screen only
    // ever wants orders still being worked on.
    List<DeliveryDetails> findByRestaurantIdAndStatusNotInOrderByCreatedAtAsc(UUID restaurantId, List<DeliveryStatus> statuses);

    // A courier's own restricted "my deliveries" screen (task 28 redesign) - scoped both to their
    // own courier_id and to OUT_FOR_DELIVERY, since marking DELIVERED is the only self-service
    // action they have; nothing still SEPARATING is theirs to act on yet.
    List<DeliveryDetails> findByRestaurantIdAndCourier_IdAndStatusOrderByCreatedAtAsc(
            UUID restaurantId, UUID courierId, DeliveryStatus status);

    // Same-day "resumo do dia" card + recent history on MyDeliveriesPage - deliveredAt (not
    // createdAt/updatedAt) is what bounds "today", see DeliveryDetails javadoc.
    List<DeliveryDetails> findByRestaurantIdAndCourier_IdAndStatusAndDeliveredAtBetweenOrderByDeliveredAtDesc(
            UUID restaurantId, UUID courierId, DeliveryStatus status, OffsetDateTime from, OffsetDateTime to);

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

    // Staff "who's free" map filter (DeliveryService#listLiveCouriers) - a courier not in this set
    // has no delivery currently out with them, so they're available for the next assignment.
    @Query("SELECT dd.courier.id FROM DeliveryDetails dd WHERE dd.restaurantId = :restaurantId AND dd.status = 'OUT_FOR_DELIVERY' AND dd.courier IS NOT NULL")
    Set<UUID> findCourierIdsWithActiveDelivery(@Param("restaurantId") UUID restaurantId);
}
