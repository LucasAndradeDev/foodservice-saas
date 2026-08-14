package com.example.warehouse.repository;

import com.example.warehouse.domain.entity.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeItemRepository extends JpaRepository<RecipeItem, UUID> {
    List<RecipeItem> findByRecipeId(UUID recipeId);
    List<RecipeItem> findByRecipeIdIn(List<UUID> recipeIds);
    void deleteByRecipeId(UUID recipeId);
}
