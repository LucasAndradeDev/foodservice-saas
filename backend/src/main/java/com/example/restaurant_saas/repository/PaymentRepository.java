package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.Payment;
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
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByTabIdOrderByPaidAtDesc(UUID tabId);

    Optional<Payment> findByIdAndTabIdAndRestaurantId(UUID id, UUID tabId, UUID restaurantId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.tab.id = :tabId AND p.status = 'ACTIVE'")
    BigDecimal sumActiveAmountByTabId(@Param("tabId") UUID tabId);

    // Net revenue = payment amount minus its tab's service charge, attributed proportionally to how
    // much this payment contributed to paying off its tab (serviceChargeAmount is one fixed value per
    // tab, not per payment, so it can't just be subtracted from every payment row independently).
    // Restricted to CLOSED tabs: a Payment can now exist against a still-OPEN tab (partial payment),
    // and revenue must only be recognized once the sale is actually complete — matches the old
    // behavior, where paidAt was only ever set at close time, so the date filter alone used to imply
    // "closed" without needing to say so.
    // NULLIF(g.tab_gross, 0) avoids a division-by-zero when every active payment on a tab is exactly
    // 0 (e.g. a tab with no items closed via a single $0 payment) — the row then contributes NULL,
    // which SUM() ignores, i.e. contributes nothing, which is the correct result for a $0 payment
    // anyway. ROUND(...,2) keeps each row a clean cent amount instead of a long division tail.
    @Query(value = "SELECT COALESCE(SUM(ROUND(p.amount - p.amount * COALESCE(t.service_charge_amount, 0) / NULLIF(g.tab_gross, 0), 2)), 0) " +
            "FROM payments p " +
            "JOIN tabs t ON t.id = p.tab_id " +
            "JOIN (SELECT tab_id, SUM(amount) AS tab_gross FROM payments WHERE status = 'ACTIVE' GROUP BY tab_id) g " +
            "  ON g.tab_id = p.tab_id " +
            "WHERE p.restaurant_id = :restaurantId AND p.status = 'ACTIVE' AND t.status = 'CLOSED' " +
            "AND p.paid_at >= :start AND p.paid_at < :end",
            nativeQuery = true)
    BigDecimal sumNetActiveAmountByRestaurantIdAndPaidAtBetween(
            @Param("restaurantId") UUID restaurantId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query(value = "SELECT p.payment_method, " +
            "COALESCE(SUM(ROUND(p.amount - p.amount * COALESCE(t.service_charge_amount, 0) / NULLIF(g.tab_gross, 0), 2)), 0), " +
            "COUNT(*) " +
            "FROM payments p " +
            "JOIN tabs t ON t.id = p.tab_id " +
            "JOIN (SELECT tab_id, SUM(amount) AS tab_gross FROM payments WHERE status = 'ACTIVE' GROUP BY tab_id) g " +
            "  ON g.tab_id = p.tab_id " +
            "WHERE p.restaurant_id = :restaurantId AND p.status = 'ACTIVE' AND t.status = 'CLOSED' " +
            "AND p.paid_at >= :start AND p.paid_at < :end " +
            "GROUP BY p.payment_method",
            nativeQuery = true)
    List<Object[]> sumNetActiveAmountGroupedByPaymentMethod(
            @Param("restaurantId") UUID restaurantId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    // Same CLOSED-only restriction as above: an OPEN tab with a partial payment isn't a closed sale yet.
    @Query(value = "SELECT COUNT(DISTINCT p.tab_id) FROM payments p " +
            "JOIN tabs t ON t.id = p.tab_id " +
            "WHERE p.restaurant_id = :restaurantId AND p.status = 'ACTIVE' AND t.status = 'CLOSED' " +
            "AND p.paid_at >= :start AND p.paid_at < :end",
            nativeQuery = true)
    long countDistinctTabsWithActivePaymentBetween(
            @Param("restaurantId") UUID restaurantId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    // Cash-in-drawer reconciliation: the raw amount collected, service charge included, exactly like
    // the amount that would have physically been handed over — no proportional attribution here.
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.restaurant.id = :restaurantId AND p.status = 'ACTIVE' AND p.paymentMethod = 'CASH' " +
            "AND p.paidAt >= :start AND p.paidAt < :end")
    BigDecimal sumActiveCashAmountByRestaurantIdAndPaidAtBetween(
            @Param("restaurantId") UUID restaurantId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}
