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

    @Query("SELECT COALESCE(SUM(t.paidAmount), 0) FROM Tab t " +
            "WHERE t.restaurant.id = :restaurantId AND t.paidAt >= :start AND t.paidAt < :end")
    BigDecimal sumPaidAmountByRestaurantIdAndPaidAtBetween(
            @Param("restaurantId") UUID restaurantId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}
