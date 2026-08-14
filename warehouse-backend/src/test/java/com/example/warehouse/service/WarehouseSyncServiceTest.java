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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
class WarehouseSyncServiceTest {

    @Autowired
    private WarehouseSyncService warehouseSyncService;
    @Autowired
    private RestaurantLinkRepository restaurantLinkRepository;
    @Autowired
    private SyncStateRepository syncStateRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private RecipeItemRepository recipeItemRepository;
    @Autowired
    private IngredientRepository ingredientRepository;

    @MockBean
    private MoraApiClient moraApiClient;

    private RestaurantLink createLink(String rawApiKey) {
        return restaurantLinkRepository.save(RestaurantLink.builder()
                .moraRestaurantId(UUID.randomUUID())
                .name("Burger House " + System.nanoTime())
                .apiKey(rawApiKey)
                .build());
    }

    private Ingredient createIngredient(UUID restaurantLinkId, String name, String quantity) {
        return ingredientRepository.save(Ingredient.builder()
                .restaurantLinkId(restaurantLinkId)
                .name(name)
                .unit("kg")
                .currentQuantity(new BigDecimal(quantity))
                .build());
    }

    private Recipe createRecipe(UUID restaurantLinkId, UUID moraProductId, UUID ingredientId, String quantityPerUnit) {
        Recipe recipe = recipeRepository.save(Recipe.builder()
                .restaurantLinkId(restaurantLinkId)
                .moraProductId(moraProductId)
                .moraProductName("Cheeseburger")
                .build());
        recipeItemRepository.save(RecipeItem.builder()
                .recipeId(recipe.getId())
                .ingredientId(ingredientId)
                .quantityPerUnit(new BigDecimal(quantityPerUnit))
                .build());
        return recipe;
    }

    @Test
    void syncAll_deductsIngredientStockViaRecipe_andAdvancesCursor() {
        String apiKey = "raw-key-" + UUID.randomUUID();
        RestaurantLink link = createLink(apiKey);
        Ingredient ingredient = createIngredient(link.getId(), "Ground beef", "10.000");
        UUID moraProductId = UUID.randomUUID();
        createRecipe(link.getId(), moraProductId, ingredient.getId(), "0.200");

        OffsetDateTime deliveredAt = OffsetDateTime.now();
        when(moraApiClient.fetchSalesSince(eq(apiKey), any()))
                .thenReturn(List.of(new MoraApiClient.SaleItem(moraProductId, "Cheeseburger", 3, deliveredAt)));

        warehouseSyncService.syncAll();

        Ingredient updated = ingredientRepository.findById(ingredient.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("9.400").compareTo(updated.getCurrentQuantity()));

        // A straight equals against OffsetDateTime.now()'s nanosecond value is flaky: Postgres'
        // timestamptz round-trip can shift the last microsecond by +/-1 (rounding, not just
        // truncation) - assert the cursor landed within a tight tolerance instead.
        SyncState state = syncStateRepository.findById(link.getId()).orElseThrow();
        long diffMicros = java.time.Duration.between(deliveredAt.toInstant(), state.getLastSyncedAt().toInstant())
                .abs().toNanos() / 1000;
        assertTrue(diffMicros <= 1, "expected cursor within 1 microsecond of deliveredAt, was off by " + diffMicros);
    }

    @Test
    void syncAll_saleWithNoMatchingRecipe_leavesStockUnchangedButAdvancesCursor() {
        String apiKey = "raw-key-" + UUID.randomUUID();
        RestaurantLink link = createLink(apiKey);
        Ingredient ingredient = createIngredient(link.getId(), "Ground beef", "10.000");
        UUID recipedProductId = UUID.randomUUID();
        createRecipe(link.getId(), recipedProductId, ingredient.getId(), "0.200");

        UUID unrelatedProductId = UUID.randomUUID();
        OffsetDateTime deliveredAt = OffsetDateTime.now();
        when(moraApiClient.fetchSalesSince(eq(apiKey), any()))
                .thenReturn(List.of(new MoraApiClient.SaleItem(unrelatedProductId, "Soda", 5, deliveredAt)));

        warehouseSyncService.syncAll();

        Ingredient updated = ingredientRepository.findById(ingredient.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("10.000").compareTo(updated.getCurrentQuantity()));

        // A straight equals against OffsetDateTime.now()'s nanosecond value is flaky: Postgres'
        // timestamptz round-trip can shift the last microsecond by +/-1 (rounding, not just
        // truncation) - assert the cursor landed within a tight tolerance instead.
        SyncState state = syncStateRepository.findById(link.getId()).orElseThrow();
        long diffMicros = java.time.Duration.between(deliveredAt.toInstant(), state.getLastSyncedAt().toInstant())
                .abs().toNanos() / 1000;
        assertTrue(diffMicros <= 1, "expected cursor within 1 microsecond of deliveredAt, was off by " + diffMicros);
    }

    @Test
    void syncAll_oneRestaurantMoraCallFails_othersStillSync() {
        String failingApiKey = "raw-key-failing-" + UUID.randomUUID();
        RestaurantLink failingLink = createLink(failingApiKey);
        when(moraApiClient.fetchSalesSince(eq(failingApiKey), any()))
                .thenThrow(new RuntimeException("Morá unreachable"));

        String workingApiKey = "raw-key-working-" + UUID.randomUUID();
        RestaurantLink workingLink = createLink(workingApiKey);
        Ingredient ingredient = createIngredient(workingLink.getId(), "Ground beef", "10.000");
        UUID moraProductId = UUID.randomUUID();
        createRecipe(workingLink.getId(), moraProductId, ingredient.getId(), "1.000");
        OffsetDateTime deliveredAt = OffsetDateTime.now();
        when(moraApiClient.fetchSalesSince(eq(workingApiKey), any()))
                .thenReturn(List.of(new MoraApiClient.SaleItem(moraProductId, "Cheeseburger", 1, deliveredAt)));

        warehouseSyncService.syncAll();

        Ingredient updated = ingredientRepository.findById(ingredient.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("9.000").compareTo(updated.getCurrentQuantity()));
        // The failing restaurant never got far enough to write a SyncState row - next run
        // retries it from the same (absent) cursor instead of silently skipping ahead.
        assertTrue(syncStateRepository.findById(failingLink.getId()).isEmpty());
    }
}
