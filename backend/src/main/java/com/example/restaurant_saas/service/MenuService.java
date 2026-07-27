package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Category;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.entity.ProductModifierGroup;
import com.example.restaurant_saas.domain.entity.ProductModifierOption;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.dto.response.ModifierGroupResponse;
import com.example.restaurant_saas.dto.response.ModifierOptionResponse;
import com.example.restaurant_saas.dto.response.PublicMenuCategoryResponse;
import com.example.restaurant_saas.dto.response.PublicMenuProductResponse;
import com.example.restaurant_saas.dto.response.PublicMenuResponse;
import com.example.restaurant_saas.dto.response.PublicMenuTableResponse;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.repository.CategoryRepository;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.ProductModifierGroupRepository;
import com.example.restaurant_saas.repository.ProductModifierOptionRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final RestaurantTableRepository tableRepository;
    private final TabRepository tabRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductModifierGroupRepository modifierGroupRepository;
    private final ProductModifierOptionRepository modifierOptionRepository;

    @Transactional(readOnly = true)
    public PublicMenuResponse getPublicMenu(String slug, UUID tableId) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        List<Product> activeProducts = productRepository.findByRestaurantIdAndActiveTrue(restaurant.getId());
        Map<UUID, List<ModifierGroupResponse>> modifierGroupsByProduct =
                fetchModifierGroupsByProduct(restaurant.getId(), activeProducts);

        List<PublicMenuCategoryResponse> categories = categoryRepository
                .findByRestaurantIdAndActiveTrue(restaurant.getId())
                .stream()
                .map(category -> toCategoryResponse(category, activeProducts, modifierGroupsByProduct))
                .filter(category -> !category.getProducts().isEmpty())
                .toList();

        String restaurantName = restaurant.getTradeName() != null ? restaurant.getTradeName() : restaurant.getName();

        PublicMenuTableResponse tableResponse = null;
        if (tableId != null) {
            RestaurantTable table = tableRepository.findByIdAndRestaurantId(tableId, restaurant.getId())
                    .filter(t -> Boolean.TRUE.equals(t.getActive()))
                    .orElseThrow(() -> new IllegalArgumentException("Table not found."));
            boolean hasDeliveredItems = tabRepository.findOpenTabByRestaurantIdAndTableId(restaurant.getId(), table.getId())
                    .map(tab -> orderItemRepository.existsByOrder_Tab_IdAndStatus(tab.getId(), ItemStatus.DELIVERED))
                    .orElse(false);

            tableResponse = PublicMenuTableResponse.builder()
                    .id(table.getId())
                    .number(table.getNumber())
                    .hasDeliveredItems(hasDeliveredItems)
                    .build();
        }

        return PublicMenuResponse.builder()
                .restaurantName(restaurantName)
                .logo(restaurant.getLogo())
                .categories(categories)
                .table(tableResponse)
                .build();
    }

    private PublicMenuCategoryResponse toCategoryResponse(
            Category category, List<Product> activeProducts, Map<UUID, List<ModifierGroupResponse>> modifierGroupsByProduct) {
        List<PublicMenuProductResponse> products = activeProducts.stream()
                .filter(product -> product.getCategory().getId().equals(category.getId()))
                .map(product -> toProductResponse(product, modifierGroupsByProduct))
                .toList();

        return PublicMenuCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .products(products)
                .build();
    }

    private PublicMenuProductResponse toProductResponse(Product product, Map<UUID, List<ModifierGroupResponse>> modifierGroupsByProduct) {
        return PublicMenuProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .soldOut(isSoldOutToday(product))
                .modifierGroups(modifierGroupsByProduct.getOrDefault(product.getId(), List.of()))
                .build();
    }

    private boolean isSoldOutToday(Product product) {
        return product.getSoldOutAt() != null
                && product.getSoldOutAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                    .equals(OffsetDateTime.now().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate());
    }

    private Map<UUID, List<ModifierGroupResponse>> fetchModifierGroupsByProduct(UUID restaurantId, List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<UUID> productIds = products.stream().map(Product::getId).toList();
        List<ProductModifierGroup> groups = modifierGroupRepository
                .findByRestaurantIdAndProductIdInOrderByCreatedAtAsc(restaurantId, productIds);
        if (groups.isEmpty()) {
            return Map.of();
        }

        List<UUID> groupIds = groups.stream().map(ProductModifierGroup::getId).toList();
        Map<UUID, List<ProductModifierOption>> optionsByGroup = modifierOptionRepository
                .findByGroupIdInOrderByCreatedAtAsc(groupIds).stream()
                .collect(Collectors.groupingBy(option -> option.getGroup().getId()));

        return groups.stream().collect(Collectors.groupingBy(
                group -> group.getProduct().getId(),
                Collectors.mapping(
                        group -> toModifierGroupResponse(group, optionsByGroup.getOrDefault(group.getId(), List.of())),
                        Collectors.toList())));
    }

    private ModifierGroupResponse toModifierGroupResponse(ProductModifierGroup group, List<ProductModifierOption> options) {
        return ModifierGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .selectionType(group.getSelectionType())
                .required(group.getRequired())
                .options(options.stream()
                        .map(option -> ModifierOptionResponse.builder()
                                .id(option.getId())
                                .name(option.getName())
                                .priceDelta(option.getPriceDelta())
                                .build())
                        .toList())
                .build();
    }
}
