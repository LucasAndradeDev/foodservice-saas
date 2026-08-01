package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.HappyHourRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HappyHourRuleRepository extends JpaRepository<HappyHourRule, UUID> {
    List<HappyHourRule> findByRestaurantIdOrderByCreatedAtAsc(UUID restaurantId);
    List<HappyHourRule> findByRestaurantIdAndActiveTrue(UUID restaurantId);
    Optional<HappyHourRule> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
