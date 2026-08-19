package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByRestaurantIdOrderByCategoryNameAscNameAsc(UUID restaurantId);
    List<Product> findByRestaurantIdAndActiveTrueOrderByCategoryNameAscNameAsc(UUID restaurantId);
    List<Product> findByRestaurantIdAndActiveTrueAndTypeOrderByNameAsc(UUID restaurantId, ProductType type);
    Optional<Product> findByIdAndRestaurantId(UUID id, UUID restaurantId);
    Optional<Product> findByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);
    boolean existsByCategoryIdAndActiveTrue(UUID categoryId);

    boolean existsByCategoryId(UUID categoryId);
    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);
    boolean existsByRestaurantIdAndNameIgnoreCaseAndIdNot(UUID restaurantId, String name, UUID id);
}
