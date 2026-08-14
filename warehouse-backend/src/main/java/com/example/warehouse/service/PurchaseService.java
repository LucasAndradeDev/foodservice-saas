package com.example.warehouse.service;

import com.example.warehouse.domain.entity.Ingredient;
import com.example.warehouse.domain.entity.Purchase;
import com.example.warehouse.domain.entity.PurchaseItem;
import com.example.warehouse.domain.entity.Supplier;
import com.example.warehouse.dto.request.CreatePurchaseItemRequest;
import com.example.warehouse.dto.request.CreatePurchaseRequest;
import com.example.warehouse.dto.response.PurchaseItemResponse;
import com.example.warehouse.dto.response.PurchaseResponse;
import com.example.warehouse.repository.IngredientRepository;
import com.example.warehouse.repository.PurchaseItemRepository;
import com.example.warehouse.repository.PurchaseRepository;
import com.example.warehouse.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A purchase records stock coming in - each item adds its quantity onto the matching
 * ingredient's balance, the mirror image of how a future sale sync will subtract from it.
 */
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final SupplierRepository supplierRepository;
    private final IngredientRepository ingredientRepository;

    @Transactional(readOnly = true)
    public List<PurchaseResponse> listPurchases(UUID restaurantLinkId) {
        List<Purchase> purchases = purchaseRepository.findByRestaurantLinkIdOrderByPurchasedAtDesc(restaurantLinkId);
        if (purchases.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<PurchaseItem>> itemsByPurchaseId = purchaseItemRepository
                .findByPurchaseIdIn(purchases.stream().map(Purchase::getId).toList()).stream()
                .collect(Collectors.groupingBy(PurchaseItem::getPurchaseId));

        Map<UUID, Supplier> suppliersById = indexById(
                supplierRepository.findAllById(purchases.stream().map(Purchase::getSupplierId).distinct().toList()),
                Supplier::getId);
        Map<UUID, Ingredient> ingredientsById = indexById(
                ingredientRepository.findAllById(itemsByPurchaseId.values().stream()
                        .flatMap(List::stream).map(PurchaseItem::getIngredientId).distinct().toList()),
                Ingredient::getId);

        return purchases.stream()
                .map(purchase -> toResponse(purchase, suppliersById.get(purchase.getSupplierId()),
                        itemsByPurchaseId.getOrDefault(purchase.getId(), List.of()), ingredientsById))
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseResponse getPurchase(UUID restaurantLinkId, UUID purchaseId) {
        Purchase purchase = purchaseRepository.findByIdAndRestaurantLinkId(purchaseId, restaurantLinkId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase not found."));
        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseId(purchaseId);
        Supplier supplier = supplierRepository.findById(purchase.getSupplierId()).orElse(null);
        Map<UUID, Ingredient> ingredientsById = indexById(
                ingredientRepository.findAllById(items.stream().map(PurchaseItem::getIngredientId).toList()),
                Ingredient::getId);

        return toResponse(purchase, supplier, items, ingredientsById);
    }

    @Transactional
    public PurchaseResponse createPurchase(UUID restaurantLinkId, CreatePurchaseRequest request) {
        Supplier supplier = supplierRepository.findByIdAndRestaurantLinkId(request.getSupplierId(), restaurantLinkId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found."));

        List<UUID> ingredientIds = request.getItems().stream().map(CreatePurchaseItemRequest::getIngredientId).toList();
        Map<UUID, Ingredient> ingredientsById = ingredientRepository.findAllById(ingredientIds).stream()
                .filter(ingredient -> ingredient.getRestaurantLinkId().equals(restaurantLinkId))
                .collect(Collectors.toMap(Ingredient::getId, ingredient -> ingredient));
        for (UUID ingredientId : ingredientIds) {
            if (!ingredientsById.containsKey(ingredientId)) {
                throw new IllegalArgumentException("Ingredient not found: " + ingredientId);
            }
        }

        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .restaurantLinkId(restaurantLinkId)
                .supplierId(supplier.getId())
                .purchasedAt(request.getPurchasedAt() != null ? request.getPurchasedAt() : OffsetDateTime.now())
                .build());

        List<PurchaseItem> items = request.getItems().stream()
                .map(itemRequest -> {
                    Ingredient ingredient = ingredientsById.get(itemRequest.getIngredientId());
                    ingredient.setCurrentQuantity(ingredient.getCurrentQuantity().add(itemRequest.getQuantity()));
                    ingredientRepository.save(ingredient);

                    return purchaseItemRepository.save(PurchaseItem.builder()
                            .purchaseId(purchase.getId())
                            .ingredientId(ingredient.getId())
                            .quantity(itemRequest.getQuantity())
                            .unitCost(itemRequest.getUnitCost())
                            .build());
                })
                .toList();

        return toResponse(purchase, supplier, items, ingredientsById);
    }

    private <T> Map<UUID, T> indexById(List<T> entities, java.util.function.Function<T, UUID> idFn) {
        return entities.stream().collect(Collectors.toMap(idFn, entity -> entity));
    }

    private PurchaseResponse toResponse(Purchase purchase, Supplier supplier, List<PurchaseItem> items, Map<UUID, Ingredient> ingredientsById) {
        List<PurchaseItemResponse> itemResponses = items.stream()
                .map(item -> {
                    Ingredient ingredient = ingredientsById.get(item.getIngredientId());
                    return PurchaseItemResponse.builder()
                            .id(item.getId())
                            .ingredientId(item.getIngredientId())
                            .ingredientName(ingredient != null ? ingredient.getName() : null)
                            .quantity(item.getQuantity())
                            .unitCost(item.getUnitCost())
                            .build();
                })
                .toList();

        return PurchaseResponse.builder()
                .id(purchase.getId())
                .supplierId(purchase.getSupplierId())
                .supplierName(supplier != null ? supplier.getName() : null)
                .purchasedAt(purchase.getPurchasedAt())
                .createdAt(purchase.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
