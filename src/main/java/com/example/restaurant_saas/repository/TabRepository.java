package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.Tab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TabRepository extends JpaRepository<Tab, UUID> {
    List<Tab> findByRestaurantId(UUID restaurantId);
    Optional<Tab> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
