package com.example.warehouse.service;

import com.example.warehouse.domain.entity.Ingredient;
import com.example.warehouse.domain.entity.Recipe;
import com.example.warehouse.domain.entity.RecipeItem;
import com.example.warehouse.dto.request.CreateRecipeRequest;
import com.example.warehouse.dto.request.RecipeItemRequest;
import com.example.warehouse.dto.request.UpdateRecipeRequest;
import com.example.warehouse.dto.response.RecipeItemResponse;
import com.example.warehouse.dto.response.RecipeResponse;
import com.example.warehouse.repository.IngredientRepository;
import com.example.warehouse.repository.RecipeItemRepository;
import com.example.warehouse.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ficha técnica: maps a Morá product to the ingredients (and quantity per unit sold) it
 * consumes - see docs/ARMAZEM_MORA.md. WarehouseSyncService reads this to turn a delivered sale
 * into ingredient consumption.
 */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final IngredientRepository ingredientRepository;

    @Transactional(readOnly = true)
    public List<RecipeResponse> listRecipes(UUID restaurantLinkId) {
        List<Recipe> recipes = recipeRepository.findByRestaurantLinkIdOrderByMoraProductNameAsc(restaurantLinkId);
        if (recipes.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<RecipeItem>> itemsByRecipeId = recipeItemRepository
                .findByRecipeIdIn(recipes.stream().map(Recipe::getId).toList()).stream()
                .collect(Collectors.groupingBy(RecipeItem::getRecipeId));
        Map<UUID, Ingredient> ingredientsById = indexById(ingredientRepository.findAllById(
                itemsByRecipeId.values().stream().flatMap(List::stream).map(RecipeItem::getIngredientId).distinct().toList()));

        return recipes.stream()
                .map(recipe -> toResponse(recipe, itemsByRecipeId.getOrDefault(recipe.getId(), List.of()), ingredientsById))
                .toList();
    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipe(UUID restaurantLinkId, UUID recipeId) {
        Recipe recipe = findByIdAndRestaurantLink(restaurantLinkId, recipeId);
        List<RecipeItem> items = recipeItemRepository.findByRecipeId(recipeId);
        Map<UUID, Ingredient> ingredientsById = indexById(
                ingredientRepository.findAllById(items.stream().map(RecipeItem::getIngredientId).toList()));
        return toResponse(recipe, items, ingredientsById);
    }

    @Transactional
    public RecipeResponse createRecipe(UUID restaurantLinkId, CreateRecipeRequest request) {
        if (recipeRepository.existsByRestaurantLinkIdAndMoraProductId(restaurantLinkId, request.getMoraProductId())) {
            throw new IllegalArgumentException("A recipe for this product already exists.");
        }

        Map<UUID, Ingredient> ingredientsById = validateIngredients(restaurantLinkId, request.getItems());

        Recipe recipe = recipeRepository.save(Recipe.builder()
                .restaurantLinkId(restaurantLinkId)
                .moraProductId(request.getMoraProductId())
                .moraProductName(request.getMoraProductName())
                .build());

        List<RecipeItem> items = saveItems(recipe.getId(), request.getItems());

        return toResponse(recipe, items, ingredientsById);
    }

    @Transactional
    public RecipeResponse updateRecipe(UUID restaurantLinkId, UUID recipeId, UpdateRecipeRequest request) {
        Recipe recipe = findByIdAndRestaurantLink(restaurantLinkId, recipeId);

        if (request.getMoraProductName() != null) {
            recipe.setMoraProductName(request.getMoraProductName());
            recipe = recipeRepository.save(recipe);
        }

        List<RecipeItem> items;
        Map<UUID, Ingredient> ingredientsById;
        if (request.getItems() != null) {
            Map<UUID, Ingredient> validated = validateIngredients(restaurantLinkId, request.getItems());
            recipeItemRepository.deleteByRecipeId(recipeId);
            items = saveItems(recipe.getId(), request.getItems());
            ingredientsById = validated;
        } else {
            items = recipeItemRepository.findByRecipeId(recipeId);
            ingredientsById = indexById(ingredientRepository.findAllById(items.stream().map(RecipeItem::getIngredientId).toList()));
        }

        return toResponse(recipe, items, ingredientsById);
    }

    private Map<UUID, Ingredient> validateIngredients(UUID restaurantLinkId, List<RecipeItemRequest> items) {
        List<UUID> ingredientIds = items.stream().map(RecipeItemRequest::getIngredientId).toList();
        Map<UUID, Ingredient> ingredientsById = ingredientRepository.findAllById(ingredientIds).stream()
                .filter(ingredient -> ingredient.getRestaurantLinkId().equals(restaurantLinkId))
                .collect(Collectors.toMap(Ingredient::getId, ingredient -> ingredient));
        for (UUID ingredientId : ingredientIds) {
            if (!ingredientsById.containsKey(ingredientId)) {
                throw new IllegalArgumentException("Ingredient not found: " + ingredientId);
            }
        }
        return ingredientsById;
    }

    private List<RecipeItem> saveItems(UUID recipeId, List<RecipeItemRequest> itemRequests) {
        return itemRequests.stream()
                .map(itemRequest -> recipeItemRepository.save(RecipeItem.builder()
                        .recipeId(recipeId)
                        .ingredientId(itemRequest.getIngredientId())
                        .quantityPerUnit(itemRequest.getQuantityPerUnit())
                        .build()))
                .toList();
    }

    private Recipe findByIdAndRestaurantLink(UUID restaurantLinkId, UUID recipeId) {
        return recipeRepository.findByIdAndRestaurantLinkId(recipeId, restaurantLinkId)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found."));
    }

    private Map<UUID, Ingredient> indexById(List<Ingredient> ingredients) {
        return ingredients.stream().collect(Collectors.toMap(Ingredient::getId, Function.identity()));
    }

    private RecipeResponse toResponse(Recipe recipe, List<RecipeItem> items, Map<UUID, Ingredient> ingredientsById) {
        List<RecipeItemResponse> itemResponses = items.stream()
                .map(item -> {
                    Ingredient ingredient = ingredientsById.get(item.getIngredientId());
                    return RecipeItemResponse.builder()
                            .id(item.getId())
                            .ingredientId(item.getIngredientId())
                            .ingredientName(ingredient != null ? ingredient.getName() : null)
                            .quantityPerUnit(item.getQuantityPerUnit())
                            .build();
                })
                .toList();

        return RecipeResponse.builder()
                .id(recipe.getId())
                .moraProductId(recipe.getMoraProductId())
                .moraProductName(recipe.getMoraProductName())
                .items(itemResponses)
                .build();
    }
}
