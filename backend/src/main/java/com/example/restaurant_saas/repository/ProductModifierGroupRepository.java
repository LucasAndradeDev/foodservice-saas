package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.ProductModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductModifierGroupRepository extends JpaRepository<ProductModifierGroup, UUID> {
    List<ProductModifierGroup> findByProductIdAndRestaurantIdOrderByCreatedAtAsc(UUID productId, UUID restaurantId);
    List<ProductModifierGroup> findByRestaurantIdAndProductIdInOrderByCreatedAtAsc(UUID restaurantId, List<UUID> productIds);
    Optional<ProductModifierGroup> findByIdAndProductIdAndRestaurantId(UUID id, UUID productId, UUID restaurantId);
}
