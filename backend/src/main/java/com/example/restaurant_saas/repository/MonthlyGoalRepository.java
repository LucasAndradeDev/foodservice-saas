package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.MonthlyGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonthlyGoalRepository extends JpaRepository<MonthlyGoal, UUID> {
    Optional<MonthlyGoal> findByRestaurantIdAndMonth(UUID restaurantId, LocalDate month);
}
