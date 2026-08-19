package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    Optional<OrderItem> findByIdAndOrder_Restaurant_Id(UUID id, UUID restaurantId);
    List<OrderItem> findByOrder_Restaurant_IdAndStatusInOrderByCreatedAtAsc(UUID restaurantId, List<ItemStatus> statuses);
    boolean existsByOrder_Tab_IdAndStatusNotIn(UUID tabId, List<ItemStatus> statuses);
    boolean existsByOrder_Tab_IdAndStatus(UUID tabId, ItemStatus status);
    boolean existsByProductId(UUID productId);
    long countByOrder_Restaurant_IdAndStatusIn(UUID restaurantId, List<ItemStatus> statuses);
    List<OrderItem> findByOrder_Tab_IdAndOrder_Restaurant_IdAndStatusAndParentOrderItemIsNull(UUID tabId, UUID restaurantId, ItemStatus status);
    List<OrderItem> findByOrder_Tab_IdAndOrder_Restaurant_IdAndStatusNotAndParentOrderItemIsNull(UUID tabId, UUID restaurantId, ItemStatus status);
    List<OrderItem> findByOrder_Tab_IdOrderByCreatedAtAsc(UUID tabId);
    boolean existsByOrder_Restaurant_IdAndStatusAndCreatedAtAfter(UUID restaurantId, ItemStatus status, OffsetDateTime after);

    // Same PENDING/createdAt shape as the one above, but excludes items belonging to a delivery
    // order that isn't paid yet (2026-08-18 decision, docs/DELIVERY.md) - those don't show up in
    // the kitchen queue at all (OrderItemService#listKitchenQueue), so the "new order" nav badge
    // must not fire for them either.
    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END FROM OrderItem oi " +
            "WHERE oi.order.restaurant.id = :restaurantId AND oi.status = 'PENDING' AND oi.createdAt > :after " +
            "AND NOT EXISTS (SELECT 1 FROM DeliveryDetails dd WHERE dd.tab = oi.order.tab AND dd.tab.status <> 'CLOSED')")
    boolean existsNewPaidKitchenPendingItem(@Param("restaurantId") UUID restaurantId, @Param("after") OffsetDateTime after);
    boolean existsByOrder_Restaurant_IdAndStatusAndUpdatedAtAfter(UUID restaurantId, ItemStatus status, OffsetDateTime after);
    List<OrderItem> findByParentOrderItem_Id(UUID parentOrderItemId);
    List<OrderItem> findByOrder_Restaurant_IdAndStatusInAndParentOrderItemIsNullOrderByCreatedAtAsc(UUID restaurantId, List<ItemStatus> statuses);

    /**
     * True when some tab in the restaurant just finished being fully delivered (no items left
     * PENDING/PREPARING/READY) after the given instant — i.e. it's newly ready to be paid out,
     * regardless of whether the customer explicitly asked for the bill.
     */
    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END FROM OrderItem oi " +
            "WHERE oi.order.restaurant.id = :restaurantId AND oi.status = 'DELIVERED' " +
            "AND oi.deliveredAt > :since AND oi.order.tab.status = 'OPEN' " +
            "AND NOT EXISTS (SELECT 1 FROM OrderItem oi2 WHERE oi2.order.tab = oi.order.tab " +
            "AND oi2.status NOT IN ('DELIVERED', 'CANCELLED'))")
    boolean existsTabJustBecameReadyToClose(@Param("restaurantId") UUID restaurantId, @Param("since") OffsetDateTime since);

    @Query("SELECT oi.product.id, oi.product.name, SUM(oi.quantity), SUM(oi.quantity * oi.unitPrice), " +
            "SUM(CASE WHEN oi.unitCostPrice IS NOT NULL THEN oi.quantity ELSE 0 END), " +
            "SUM(CASE WHEN oi.unitCostPrice IS NOT NULL THEN oi.quantity * oi.unitCostPrice ELSE 0 END), " +
            "SUM(CASE WHEN oi.unitCostPrice IS NOT NULL THEN oi.quantity * oi.unitPrice ELSE 0 END) FROM OrderItem oi " +
            "WHERE oi.order.restaurant.id = :restaurantId AND oi.order.tab.status = 'CLOSED' " +
            "AND oi.order.tab.closedAt >= :start AND oi.order.tab.closedAt < :end " +
            "GROUP BY oi.product.id, oi.product.name ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopSellingProducts(
            @Param("restaurantId") UUID restaurantId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end, Pageable pageable);

    @Query("SELECT oi.product.id, oi.createdAt, oi.deliveredAt FROM OrderItem oi " +
            "WHERE oi.order.restaurant.id = :restaurantId AND oi.status = 'DELIVERED' AND oi.deliveredAt IS NOT NULL")
    List<Object[]> findDeliveredTimingsByRestaurant(@Param("restaurantId") UUID restaurantId);

    @Query("SELECT oi FROM OrderItem oi " +
            "JOIN FETCH oi.order o " +
            "LEFT JOIN FETCH o.createdBy " +
            "LEFT JOIN FETCH oi.modifiers " +
            "WHERE o.restaurant.id = :restaurantId AND o.tab.status = 'CLOSED' " +
            "AND o.tab.closedAt >= :start AND o.tab.closedAt < :end " +
            "AND oi.status = 'DELIVERED' AND oi.deliveredAt IS NOT NULL AND oi.parentOrderItem IS NULL")
    List<OrderItem> findForWaiterPerformance(
            @Param("restaurantId") UUID restaurantId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    // Leaf items only (oi.children IS EMPTY) - excludes combo header rows so Armazém Morá's stock
    // sync sees the exploded components (each with its own real product), never the combo product
    // as a single line. Plain (non-combo) items and combo children both satisfy "no children of
    // their own", which is exactly the leaf set: see ComboExplodeService for how children are built.
    @Query("SELECT oi FROM OrderItem oi " +
            "JOIN FETCH oi.product " +
            "WHERE oi.order.restaurant.id = :restaurantId AND oi.status = 'DELIVERED' " +
            "AND oi.deliveredAt > :since AND oi.children IS EMPTY " +
            "ORDER BY oi.deliveredAt ASC")
    List<OrderItem> findDeliveredLeafItemsSince(@Param("restaurantId") UUID restaurantId, @Param("since") OffsetDateTime since);
}
