package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByRestaurantId(UUID restaurantId);
    Optional<Product> findByIdAndRestaurantId(UUID id, UUID restaurantId);
    boolean existsByCategoryIdAndActiveTrue(UUID categoryId);
    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);
    boolean existsByRestaurantIdAndNameIgnoreCaseAndIdNot(UUID restaurantId, String name, UUID id);
}
