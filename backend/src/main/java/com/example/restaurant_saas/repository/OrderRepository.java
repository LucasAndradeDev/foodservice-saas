package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByTabIdAndRestaurantId(UUID tabId, UUID restaurantId);
    Optional<Order> findByIdAndRestaurantId(UUID id, UUID restaurantId);
    boolean existsByTabId(UUID tabId);
    List<Order> findByTabIdAndMergedFromTabId(UUID tabId, UUID mergedFromTabId);
}
