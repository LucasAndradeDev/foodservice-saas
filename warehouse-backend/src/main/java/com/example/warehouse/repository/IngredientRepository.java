package com.example.warehouse.repository;

import com.example.warehouse.domain.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {
    List<Ingredient> findByRestaurantLinkIdOrderByNameAsc(UUID restaurantLinkId);
    Optional<Ingredient> findByIdAndRestaurantLinkId(UUID id, UUID restaurantLinkId);
    boolean existsByRestaurantLinkIdAndNameIgnoreCase(UUID restaurantLinkId, String name);
    boolean existsByRestaurantLinkIdAndNameIgnoreCaseAndIdNot(UUID restaurantLinkId, String name, UUID id);
}
