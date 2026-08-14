package com.example.warehouse.repository;

import com.example.warehouse.domain.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
    List<Recipe> findByRestaurantLinkIdOrderByMoraProductNameAsc(UUID restaurantLinkId);
    Optional<Recipe> findByIdAndRestaurantLinkId(UUID id, UUID restaurantLinkId);
    boolean existsByRestaurantLinkIdAndMoraProductId(UUID restaurantLinkId, UUID moraProductId);
}
