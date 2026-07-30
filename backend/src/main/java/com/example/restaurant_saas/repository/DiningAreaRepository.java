package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.DiningArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiningAreaRepository extends JpaRepository<DiningArea, UUID> {
    List<DiningArea> findByRestaurantIdOrderByDisplayOrderAsc(UUID restaurantId);
    Optional<DiningArea> findByIdAndRestaurantId(UUID id, UUID restaurantId);
    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);
    boolean existsByRestaurantIdAndNameIgnoreCaseAndIdNot(UUID restaurantId, String name, UUID id);
    long countByRestaurantId(UUID restaurantId);
}
