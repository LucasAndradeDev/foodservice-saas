package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Category;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.entity.ProductModifierGroup;
import com.example.restaurant_saas.domain.entity.ProductModifierOption;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.dto.response.ModifierGroupResponse;
import com.example.restaurant_saas.dto.response.ModifierOptionResponse;
import com.example.restaurant_saas.dto.response.PublicMenuCategoryResponse;
import com.example.restaurant_saas.dto.response.PublicMenuOrderItemResponse;
import com.example.restaurant_saas.dto.response.PublicMenuProductResponse;
import com.example.restaurant_saas.dto.response.PublicMenuReorderItemResponse;
import com.example.restaurant_saas.dto.response.PublicMenuReorderModifierResponse;
import com.example.restaurant_saas.dto.response.PublicMenuResponse;
import com.example.restaurant_saas.dto.response.PublicMenuTableResponse;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.repository.CategoryRepository;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.ProductModifierGroupRepository;
import com.example.restaurant_saas.repository.ProductModifierOptionRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private static final int BESTSELLER_LIMIT = 3;
    private static final int BESTSELLER_WINDOW_DAYS = 30;

    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final RestaurantTableRepository tableRepository;
    private final TabRepository tabRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductModifierGroupRepository modifierGroupRepository;
    private final ProductModifierOptionRepository modifierOptionRepository;

    @Transactional(readOnly = true)
    public PublicMenuResponse getPublicMenu(String slug, UUID tableId) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        List<Product> activeProducts = productRepository.findByRestaurantIdAndActiveTrueOrderByCategoryNameAscNameAsc(restaurant.getId());
        Map<UUID, List<ModifierGroupResponse>> modifierGroupsByProduct =
                fetchModifierGroupsByProduct(restaurant.getId(), activeProducts);
        Map<UUID, Integer> estimatedWaitMinutesByProduct = fetchEstimatedWaitMinutesByProduct(restaurant.getId());
        Set<UUID> bestsellerProductIds = fetchBestsellerProductIds(restaurant.getId());

        List<PublicMenuCategoryResponse> categories = categoryRepository
                .findByRestaurantIdAndActiveTrueOrderByNameAsc(restaurant.getId())
                .stream()
                .map(category -> toCategoryResponse(category, activeProducts, modifierGroupsByProduct, estimatedWaitMinutesByProduct, bestsellerProductIds))
                .filter(category -> !category.getProducts().isEmpty())
                .toList();

        String restaurantName = restaurant.getTradeName() != null ? restaurant.getTradeName() : restaurant.getName();

        PublicMenuTableResponse tableResponse = null;
        if (tableId != null) {
            RestaurantTable table = tableRepository.findByIdAndRestaurantId(tableId, restaurant.getId())
                    .filter(t -> Boolean.TRUE.equals(t.getActive()))
                    .orElseThrow(() -> new IllegalArgumentException("Table not found."));
            Optional<Tab> openTab = tabRepository.findOpenTabByRestaurantIdAndTableId(restaurant.getId(), table.getId());
            boolean hasDeliveredItems = openTab
                    .map(tab -> orderItemRepository.existsByOrder_Tab_IdAndStatus(tab.getId(), ItemStatus.DELIVERED))
                    .orElse(false);
            List<PublicMenuOrderItemResponse> orderItems = openTab
                    .map(tab -> orderItemRepository.findByOrder_Tab_IdOrderByCreatedAtAsc(tab.getId()).stream()
                            .map(this::toOrderItemStatusResponse)
                            .toList())
                    .orElse(List.of());
            List<PublicMenuReorderItemResponse> lastOrderItems = openTab
                    .flatMap(tab -> orderRepository.findFirstByTabIdAndRestaurantIdOrderByCreatedAtDesc(tab.getId(), restaurant.getId()))
                    .map(order -> order.getItems().stream()
                            .filter(item -> item.getStatus() != ItemStatus.CANCELLED)
                            .map(this::toReorderItemResponse)
                            .toList())
                    .orElse(List.of());

            tableResponse = PublicMenuTableResponse.builder()
                    .id(table.getId())
                    .number(table.getNumber())
                    .hasDeliveredItems(hasDeliveredItems)
                    .orderItems(orderItems)
                    .lastOrderItems(lastOrderItems)
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
            Category category, List<Product> activeProducts, Map<UUID, List<ModifierGroupResponse>> modifierGroupsByProduct,
            Map<UUID, Integer> estimatedWaitMinutesByProduct, Set<UUID> bestsellerProductIds) {
        List<PublicMenuProductResponse> products = activeProducts.stream()
                .filter(product -> product.getCategory().getId().equals(category.getId()))
                .map(product -> toProductResponse(product, modifierGroupsByProduct, estimatedWaitMinutesByProduct, bestsellerProductIds))
                .toList();

        return PublicMenuCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .products(products)
                .build();
    }

    private PublicMenuProductResponse toProductResponse(
            Product product, Map<UUID, List<ModifierGroupResponse>> modifierGroupsByProduct,
            Map<UUID, Integer> estimatedWaitMinutesByProduct, Set<UUID> bestsellerProductIds) {
        return PublicMenuProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .soldOut(isSoldOutToday(product))
                .featured(product.getFeatured())
                .bestseller(bestsellerProductIds.contains(product.getId()))
                .estimatedWaitMinutes(estimatedWaitMinutesByProduct.get(product.getId()))
                .modifierGroups(modifierGroupsByProduct.getOrDefault(product.getId(), List.of()))
                .build();
    }

    private Set<UUID> fetchBestsellerProductIds(UUID restaurantId) {
        OffsetDateTime rangeEnd = OffsetDateTime.now();
        OffsetDateTime rangeStart = rangeEnd.minusDays(BESTSELLER_WINDOW_DAYS);
        Set<UUID> productIds = new HashSet<>();
        for (Object[] row : orderItemRepository.findTopSellingProducts(restaurantId, rangeStart, rangeEnd, PageRequest.of(0, BESTSELLER_LIMIT))) {
            productIds.add((UUID) row[0]);
        }
        return productIds;
    }

    private Map<UUID, Integer> fetchEstimatedWaitMinutesByProduct(UUID restaurantId) {
        Map<UUID, List<Long>> minutesByProduct = new HashMap<>();
        for (Object[] row : orderItemRepository.findDeliveredTimingsByRestaurant(restaurantId)) {
            UUID productId = (UUID) row[0];
            OffsetDateTime createdAt = (OffsetDateTime) row[1];
            OffsetDateTime deliveredAt = (OffsetDateTime) row[2];
            minutesByProduct.computeIfAbsent(productId, key -> new ArrayList<>())
                    .add(Duration.between(createdAt, deliveredAt).toMinutes());
        }
        return minutesByProduct.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> (int) Math.round(entry.getValue().stream().mapToLong(Long::longValue).average().orElse(0))));
    }

    private PublicMenuOrderItemResponse toOrderItemStatusResponse(OrderItem item) {
        return PublicMenuOrderItemResponse.builder()
                .id(item.getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .status(item.getStatus())
                .build();
    }

    private PublicMenuReorderItemResponse toReorderItemResponse(OrderItem item) {
        return PublicMenuReorderItemResponse.builder()
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .observation(item.getObservation())
                .modifiers(item.getModifiers().stream()
                        .map(modifier -> PublicMenuReorderModifierResponse.builder()
                                .groupName(modifier.getGroupName())
                                .optionName(modifier.getOptionName())
                                .build())
                        .toList())
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
