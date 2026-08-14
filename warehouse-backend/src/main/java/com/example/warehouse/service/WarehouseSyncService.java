package com.example.warehouse.service;

import com.example.warehouse.domain.entity.Ingredient;
import com.example.warehouse.domain.entity.Recipe;
import com.example.warehouse.domain.entity.RecipeItem;
import com.example.warehouse.domain.entity.RestaurantLink;
import com.example.warehouse.domain.entity.SyncState;
import com.example.warehouse.repository.IngredientRepository;
import com.example.warehouse.repository.RecipeItemRepository;
import com.example.warehouse.repository.RecipeRepository;
import com.example.warehouse.repository.RestaurantLinkRepository;
import com.example.warehouse.repository.SyncStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pulls delivered sales from Morá (see MoraApiClient) and turns them into ingredient consumption
 * via each product's recipe - the "baixa automática por venda" pillar of Armazém Morá
 * (docs/ARMAZEM_MORA.md). Runs for every RestaurantLink, triggered by
 * WarehouseSyncController#triggerSync on the GitHub Actions cron schedule (Render's free tier
 * can't be trusted to run an internal @Scheduled job, same reasoning as the backup job).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseSyncService {

    private final RestaurantLinkRepository restaurantLinkRepository;
    private final SyncStateRepository syncStateRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final IngredientRepository ingredientRepository;
    private final MoraApiClient moraApiClient;

    @Transactional
    public void syncAll() {
        for (RestaurantLink link : restaurantLinkRepository.findAll()) {
            try {
                syncOne(link);
            } catch (Exception ex) {
                // One restaurant's Morá call failing (network blip, stale key) must not stop the
                // rest from syncing - log and move on, the next 15-minute run picks it back up
                // from the same "since" cursor since it's never advanced on failure.
                log.error("Warehouse sync failed for restaurant link {}", link.getId(), ex);
            }
        }
    }

    private void syncOne(RestaurantLink link) {
        OffsetDateTime since = syncStateRepository.findById(link.getId())
                .map(SyncState::getLastSyncedAt)
                .orElse(link.getCreatedAt());

        List<MoraApiClient.SaleItem> sales = moraApiClient.fetchSalesSince(link.getApiKey(), since);
        if (sales.isEmpty()) {
            return;
        }

        List<Recipe> recipes = recipeRepository.findByRestaurantLinkIdOrderByMoraProductNameAsc(link.getId());
        Map<UUID, Recipe> recipesByMoraProductId = recipes.stream()
                .collect(Collectors.toMap(Recipe::getMoraProductId, recipe -> recipe));
        Map<UUID, List<RecipeItem>> itemsByRecipeId = recipeItemRepository
                .findByRecipeIdIn(recipes.stream().map(Recipe::getId).toList()).stream()
                .collect(Collectors.groupingBy(RecipeItem::getRecipeId));

        Map<UUID, Ingredient> ingredientCache = new HashMap<>();
        OffsetDateTime maxDeliveredAt = since;

        for (MoraApiClient.SaleItem sale : sales) {
            if (sale.deliveredAt() != null && sale.deliveredAt().isAfter(maxDeliveredAt)) {
                maxDeliveredAt = sale.deliveredAt();
            }

            Recipe recipe = recipesByMoraProductId.get(sale.productId());
            if (recipe == null) {
                // No ficha técnica configured for this product yet - not an error, just nothing
                // to deduct. The sale is still "seen" for the purposes of advancing the cursor.
                continue;
            }

            for (RecipeItem recipeItem : itemsByRecipeId.getOrDefault(recipe.getId(), List.of())) {
                Ingredient ingredient = ingredientCache.computeIfAbsent(recipeItem.getIngredientId(),
                        id -> ingredientRepository.findById(id).orElse(null));
                if (ingredient == null) {
                    continue;
                }
                BigDecimal consumed = recipeItem.getQuantityPerUnit().multiply(BigDecimal.valueOf(sale.quantity()));
                ingredient.setCurrentQuantity(ingredient.getCurrentQuantity().subtract(consumed));
            }
        }

        ingredientRepository.saveAll(ingredientCache.values().stream().filter(java.util.Objects::nonNull).toList());

        SyncState state = syncStateRepository.findById(link.getId())
                .orElseGet(() -> SyncState.builder().restaurantLinkId(link.getId()).build());
        state.setLastSyncedAt(maxDeliveredAt);
        syncStateRepository.save(state);
    }
}
