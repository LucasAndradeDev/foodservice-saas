package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.PixIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PixIntegrationRepository extends JpaRepository<PixIntegration, UUID> {
    Optional<PixIntegration> findByRestaurantId(UUID restaurantId);
}
