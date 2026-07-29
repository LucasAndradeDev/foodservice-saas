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
    List<OrderItem> findByOrder_Tab_IdAndOrder_Restaurant_IdAndStatus(UUID tabId, UUID restaurantId, ItemStatus status);
    List<OrderItem> findByOrder_Tab_IdOrderByCreatedAtAsc(UUID tabId);

    @Query("SELECT oi.product.id, oi.product.name, SUM(oi.quantity), SUM(oi.quantity * oi.unitPrice), " +
            "SUM(CASE WHEN oi.unitCostPrice IS NOT NULL THEN oi.quantity ELSE 0 END), " +
            "SUM(CASE WHEN oi.unitCostPrice IS NOT NULL THEN oi.quantity * oi.unitCostPrice ELSE 0 END), " +
            "SUM(CASE WHEN oi.unitCostPrice IS NOT NULL THEN oi.quantity * oi.unitPrice ELSE 0 END) FROM OrderItem oi " +
            "WHERE oi.order.restaurant.id = :restaurantId AND oi.order.tab.status = 'CLOSED' " +
            "AND oi.order.tab.paidAt >= :start AND oi.order.tab.paidAt < :end " +
            "GROUP BY oi.product.id, oi.product.name ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopSellingProducts(
            @Param("restaurantId") UUID restaurantId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end, Pageable pageable);

    @Query("SELECT oi.product.id, oi.createdAt, oi.deliveredAt FROM OrderItem oi " +
            "WHERE oi.order.restaurant.id = :restaurantId AND oi.status = 'DELIVERED' AND oi.deliveredAt IS NOT NULL")
    List<Object[]> findDeliveredTimingsByRestaurant(@Param("restaurantId") UUID restaurantId);
}
