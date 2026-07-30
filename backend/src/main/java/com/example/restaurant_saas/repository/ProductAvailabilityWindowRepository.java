package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.ProductAvailabilityWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductAvailabilityWindowRepository extends JpaRepository<ProductAvailabilityWindow, UUID> {
    List<ProductAvailabilityWindow> findByProductIdAndRestaurantIdOrderByCreatedAtAsc(UUID productId, UUID restaurantId);
    List<ProductAvailabilityWindow> findByRestaurantIdAndProductIdInOrderByCreatedAtAsc(UUID restaurantId, List<UUID> productIds);
    Optional<ProductAvailabilityWindow> findByIdAndProductIdAndRestaurantId(UUID id, UUID productId, UUID restaurantId);
}
