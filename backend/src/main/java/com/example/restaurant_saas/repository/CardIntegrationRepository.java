package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.CardIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardIntegrationRepository extends JpaRepository<CardIntegration, UUID> {
    Optional<CardIntegration> findByRestaurantId(UUID restaurantId);
}
