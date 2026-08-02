package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.Tab;
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
public interface TabRepository extends JpaRepository<Tab, UUID> {
    List<Tab> findByRestaurantId(UUID restaurantId);
    Optional<Tab> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    @Query("SELECT COALESCE(SUM(t.paidAmount - COALESCE(t.serviceChargeAmount, 0)), 0) FROM Tab t " +
            "WHERE t.restaurant.id = :restaurantId AND t.paidAt >= :start AND t.paidAt < :end")
    BigDecimal sumPaidAmountByRestaurantIdAndPaidAtBetween(
            @Param("restaurantId") UUID restaurantId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query("SELECT t.paymentMethod, COALESCE(SUM(t.paidAmount - COALESCE(t.serviceChargeAmount, 0)), 0), COUNT(t) FROM Tab t " +
            "WHERE t.restaurant.id = :restaurantId AND t.status = 'CLOSED' AND t.paidAt >= :start AND t.paidAt < :end " +
            "GROUP BY t.paymentMethod")
    List<Object[]> sumPaidAmountByRestaurantIdAndPaidAtBetweenGroupedByPaymentMethod(
            @Param("restaurantId") UUID restaurantId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query("SELECT t FROM Tab t JOIN t.tables rt WHERE rt.id = :tableId AND t.restaurant.id = :restaurantId AND t.status = 'OPEN'")
    Optional<Tab> findOpenTabByRestaurantIdAndTableId(@Param("restaurantId") UUID restaurantId, @Param("tableId") UUID tableId);

    @Query("SELECT DISTINCT t FROM Tab t JOIN t.tables rt " +
            "WHERE t.restaurant.id = :restaurantId AND t.closedAt IS NOT NULL " +
            "AND t.openedAt < :rangeEnd AND t.closedAt > :rangeStart")
    List<Tab> findClosedTabsWithTablesOverlappingRange(
            @Param("restaurantId") UUID restaurantId, @Param("rangeStart") OffsetDateTime rangeStart, @Param("rangeEnd") OffsetDateTime rangeEnd);

    @Query("SELECT COALESCE(SUM(t.paidAmount), 0) FROM Tab t " +
            "WHERE t.restaurant.id = :restaurantId AND t.paymentMethod = 'CASH' " +
            "AND t.paidAt >= :start AND t.paidAt < :end")
    BigDecimal sumCashPaymentsByRestaurantIdAndPaidAtBetween(
            @Param("restaurantId") UUID restaurantId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}
