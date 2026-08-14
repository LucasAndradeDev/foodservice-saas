package com.example.warehouse.repository;

import com.example.warehouse.domain.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
    List<Purchase> findByRestaurantLinkIdOrderByPurchasedAtDesc(UUID restaurantLinkId);
    Optional<Purchase> findByIdAndRestaurantLinkId(UUID id, UUID restaurantLinkId);
}
