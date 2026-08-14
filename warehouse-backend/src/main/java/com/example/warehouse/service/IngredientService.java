package com.example.warehouse.service;

import com.example.warehouse.domain.entity.Ingredient;
import com.example.warehouse.dto.request.CreateIngredientRequest;
import com.example.warehouse.dto.request.UpdateIngredientRequest;
import com.example.warehouse.dto.response.IngredientResponse;
import com.example.warehouse.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredientRepository;

    @Transactional(readOnly = true)
    public List<IngredientResponse> listIngredients(UUID restaurantLinkId, Boolean activeFilter) {
        return ingredientRepository.findByRestaurantLinkIdOrderByNameAsc(restaurantLinkId).stream()
                .filter(ingredient -> activeFilter == null || activeFilter.equals(ingredient.getActive()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public IngredientResponse getIngredient(UUID restaurantLinkId, UUID ingredientId) {
        return toResponse(findByIdAndRestaurantLink(restaurantLinkId, ingredientId));
    }

    @Transactional
    public IngredientResponse createIngredient(UUID restaurantLinkId, CreateIngredientRequest request) {
        if (ingredientRepository.existsByRestaurantLinkIdAndNameIgnoreCase(restaurantLinkId, request.getName())) {
            throw new IllegalArgumentException("An ingredient with this name already exists.");
        }

        Ingredient ingredient = Ingredient.builder()
                .restaurantLinkId(restaurantLinkId)
                .name(request.getName())
                .unit(request.getUnit())
                .currentQuantity(request.getCurrentQuantity() != null ? request.getCurrentQuantity() : BigDecimal.ZERO)
                .lowStockThreshold(request.getLowStockThreshold())
                .active(true)
                .build();

        return toResponse(ingredientRepository.save(ingredient));
    }

    @Transactional
    public IngredientResponse updateIngredient(UUID restaurantLinkId, UUID ingredientId, UpdateIngredientRequest request) {
        Ingredient ingredient = findByIdAndRestaurantLink(restaurantLinkId, ingredientId);

        if (request.getName() != null) {
            if (ingredientRepository.existsByRestaurantLinkIdAndNameIgnoreCaseAndIdNot(restaurantLinkId, request.getName(), ingredientId)) {
                throw new IllegalArgumentException("An ingredient with this name already exists.");
            }
            ingredient.setName(request.getName());
        }
        if (request.getUnit() != null) {
            ingredient.setUnit(request.getUnit());
        }
        if (request.getCurrentQuantity() != null) {
            ingredient.setCurrentQuantity(request.getCurrentQuantity());
        }
        if (request.getLowStockThreshold() != null) {
            ingredient.setLowStockThreshold(request.getLowStockThreshold());
        }
        if (request.getActive() != null) {
            ingredient.setActive(request.getActive());
        }

        return toResponse(ingredientRepository.save(ingredient));
    }

    private Ingredient findByIdAndRestaurantLink(UUID restaurantLinkId, UUID ingredientId) {
        return ingredientRepository.findByIdAndRestaurantLinkId(ingredientId, restaurantLinkId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found."));
    }

    private IngredientResponse toResponse(Ingredient ingredient) {
        boolean lowStock = ingredient.getLowStockThreshold() != null
                && ingredient.getCurrentQuantity().compareTo(ingredient.getLowStockThreshold()) <= 0;

        return IngredientResponse.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .unit(ingredient.getUnit())
                .currentQuantity(ingredient.getCurrentQuantity())
                .lowStockThreshold(ingredient.getLowStockThreshold())
                .active(ingredient.getActive())
                .lowStock(lowStock)
                .build();
    }
}
