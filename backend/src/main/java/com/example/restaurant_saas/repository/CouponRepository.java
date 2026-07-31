package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    List<Coupon> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId);
    Optional<Coupon> findByIdAndRestaurantId(UUID id, UUID restaurantId);
    Optional<Coupon> findByRestaurantIdAndCodeIgnoreCase(UUID restaurantId, String code);
    boolean existsByRestaurantIdAndCodeIgnoreCase(UUID restaurantId, String code);
    boolean existsByRestaurantIdAndCodeIgnoreCaseAndIdNot(UUID restaurantId, String code, UUID id);

    /**
     * Atomically claims one use of the coupon, guarding against a race where two redemptions
     * both read a usedCount just under maxUses. Returns the number of rows updated: 1 if the
     * slot was claimed, 0 if maxUses was already reached (caller should treat that as "sold out").
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount + 1 WHERE c.id = :id AND (c.maxUses IS NULL OR c.usedCount < c.maxUses)")
    int incrementUsage(@Param("id") UUID id);

    /** Gives back the usage slot claimed by incrementUsage when a redemption is undone. */
    @Modifying
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount - 1 WHERE c.id = :id AND c.usedCount > 0")
    void decrementUsage(@Param("id") UUID id);
}
