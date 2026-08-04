package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.HappyHourRule;
import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.OrderItemModifier;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.entity.ProductAvailabilityWindow;
import com.example.restaurant_saas.domain.entity.ProductModifierGroup;
import com.example.restaurant_saas.domain.entity.ProductModifierOption;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.ModifierSelectionType;
import com.example.restaurant_saas.domain.enums.ProductType;
import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.response.OrderItemModifierResponse;
import com.example.restaurant_saas.dto.response.OrderItemResponse;
import com.example.restaurant_saas.dto.response.OrderResponse;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.ProductAvailabilityWindowRepository;
import com.example.restaurant_saas.repository.ProductModifierGroupRepository;
import com.example.restaurant_saas.repository.ProductModifierOptionRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.TabRepository;
import com.example.restaurant_saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final TabRepository tabRepository;
    private final ProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final ProductModifierGroupRepository modifierGroupRepository;
    private final ProductModifierOptionRepository modifierOptionRepository;
    private final ProductAvailabilityWindowRepository availabilityWindowRepository;
    private final ComboExplodeService comboExplodeService;
    private final HappyHourRuleService happyHourRuleService;

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(UUID restaurantId, UUID tabId) {
        return orderRepository.findByTabIdAndRestaurantId(tabId, restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID restaurantId, UUID orderId) {
        return toResponse(findByIdAndRestaurant(restaurantId, orderId));
    }

    @Transactional
    public OrderResponse createOrder(UUID restaurantId, UUID tabId, CreateOrderRequest request, UUID createdByUserId) {
        // Locked (not a plain read) so this can't race a concurrent registerPayments/applyDiscount call:
        // without the lock, an order could be inserted after another transaction already froze the
        // tab's bill total, adding an item that's never actually billed.
        Tab tab = tabRepository.findByIdAndRestaurantIdForUpdate(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalStateException("Tab is not open.");
        }
        if (tab.getBillTotal() != null) {
            throw new IllegalStateException("Payment has already started for this tab.");
        }

        Order order = Order.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .tab(tab)
                .createdBy(createdByUserId != null ? userRepository.getReferenceById(createdByUserId) : null)
                .build();

        Map<UUID, HappyHourRule> activeHappyHourRuleByCategory = happyHourRuleService.fetchActiveRulesByCategory(restaurantId);
        List<OrderItem> items = request.getItems().stream()
                .flatMap(itemRequest -> toOrderItems(restaurantId, order, itemRequest, activeHappyHourRuleByCategory).stream())
                .toList();
        order.setItems(items);

        tab.setLastOrderAt(OffsetDateTime.now());

        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse markPrinted(UUID restaurantId, UUID orderId) {
        Order order = findByIdAndRestaurant(restaurantId, orderId);
        order.setPrintedAt(OffsetDateTime.now());
        return toResponse(orderRepository.save(order));
    }

    private List<OrderItem> toOrderItems(UUID restaurantId, Order order, CreateOrderItemRequest itemRequest, Map<UUID, HappyHourRule> activeHappyHourRuleByCategory) {
        Product product = productRepository.findByIdAndRestaurantId(itemRequest.getProductId(), restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));

        if (product.getType() != ProductType.COMBO) {
            if (itemRequest.getSlotSelections() != null && !itemRequest.getSlotSelections().isEmpty()) {
                throw new IllegalArgumentException("Product " + product.getName() + " is not a combo.");
            }
            return List.of(toOrderItem(restaurantId, order, product, itemRequest, activeHappyHourRuleByCategory));
        }

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalStateException("Product " + product.getName() + " is not active.");
        }
        if (!isAvailableNow(restaurantId, product)) {
            throw new IllegalStateException("Product " + product.getName() + " is not available at this time.");
        }

        List<OrderItem> flattened = new ArrayList<>();
        for (int i = 0; i < itemRequest.getQuantity(); i++) {
            OrderItem header = comboExplodeService.explodeUnit(restaurantId, order, product, itemRequest);
            flattened.add(header);
            flattened.addAll(header.getChildren());
        }
        return flattened;
    }

    private OrderItem toOrderItem(UUID restaurantId, Order order, Product product, CreateOrderItemRequest itemRequest, Map<UUID, HappyHourRule> activeHappyHourRuleByCategory) {
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalStateException("Product " + product.getName() + " is not active.");
        }
        if (!isAvailableNow(restaurantId, product)) {
            throw new IllegalStateException("Product " + product.getName() + " is not available at this time.");
        }

        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .quantity(itemRequest.getQuantity())
                .unitPrice(product.getPrice())
                .unitCostPrice(product.getCostPrice())
                .observation(itemRequest.getObservation())
                .status(ItemStatus.PENDING)
                .build();

        List<OrderItemModifier> modifiers = resolveModifiers(restaurantId, product, itemRequest.getSelectedOptionIds());
        modifiers.forEach(modifier -> modifier.setOrderItem(item));
        item.setModifiers(modifiers);

        HappyHourRule happyHourRule = activeHappyHourRuleByCategory.get(product.getCategory().getId());
        if (happyHourRule != null) {
            item.setDiscountType(happyHourRule.getDiscountType());
            item.setDiscountValue(happyHourRule.getDiscountValue());
            item.setDiscountReason("Happy hour");
            item.setDiscountAppliedBy("SISTEMA");
            item.setDiscountAppliedAt(OffsetDateTime.now());
        }

        return item;
    }

    private boolean isAvailableNow(UUID restaurantId, Product product) {
        List<ProductAvailabilityWindow> windows = availabilityWindowRepository
                .findByProductIdAndRestaurantIdOrderByCreatedAtAsc(product.getId(), restaurantId);
        if (windows.isEmpty()) {
            return true;
        }

        ZonedDateTime now = OffsetDateTime.now().atZoneSameInstant(ZoneId.systemDefault());
        DayOfWeek today = now.getDayOfWeek();
        LocalTime timeNow = now.toLocalTime();

        return windows.stream().anyMatch(window ->
                (window.getDayOfWeek() == null || window.getDayOfWeek() == today)
                        && !timeNow.isBefore(window.getStartTime())
                        && !timeNow.isAfter(window.getEndTime()));
    }

    private List<OrderItemModifier> resolveModifiers(UUID restaurantId, Product product, List<UUID> selectedOptionIds) {
        List<UUID> optionIds = selectedOptionIds == null ? List.of() : selectedOptionIds;

        List<ProductModifierGroup> groups = modifierGroupRepository
                .findByProductIdAndRestaurantIdOrderByCreatedAtAsc(product.getId(), restaurantId);
        if (groups.isEmpty()) {
            if (!optionIds.isEmpty()) {
                throw new IllegalArgumentException("This product has no modifiers to select.");
            }
            return new ArrayList<>();
        }

        List<UUID> groupIds = groups.stream().map(ProductModifierGroup::getId).toList();
        Map<UUID, ProductModifierOption> optionsById = modifierOptionRepository.findByGroupIdInOrderByCreatedAtAsc(groupIds).stream()
                .collect(Collectors.toMap(ProductModifierOption::getId, option -> option));

        List<ProductModifierOption> selectedOptions = new ArrayList<>();
        for (UUID optionId : optionIds) {
            ProductModifierOption option = optionsById.get(optionId);
            if (option == null) {
                throw new IllegalArgumentException("Selected modifier option not found for this product.");
            }
            selectedOptions.add(option);
        }

        Map<UUID, List<ProductModifierOption>> selectedByGroup = selectedOptions.stream()
                .collect(Collectors.groupingBy(option -> option.getGroup().getId()));

        for (ProductModifierGroup group : groups) {
            List<ProductModifierOption> selectedForGroup = selectedByGroup.getOrDefault(group.getId(), List.of());
            if (group.getSelectionType() == ModifierSelectionType.SINGLE && selectedForGroup.size() > 1) {
                throw new IllegalArgumentException("Group " + group.getName() + " allows only one option.");
            }
            if (Boolean.TRUE.equals(group.getRequired()) && selectedForGroup.isEmpty()) {
                throw new IllegalArgumentException("Select an option for " + group.getName() + ".");
            }
        }

        return selectedOptions.stream()
                .map(option -> OrderItemModifier.builder()
                        .restaurant(restaurantRepository.getReferenceById(restaurantId))
                        .groupName(option.getGroup().getName())
                        .optionName(option.getName())
                        .priceDelta(option.getPriceDelta())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Order findByIdAndRestaurant(UUID restaurantId, UUID orderId) {
        return orderRepository.findByIdAndRestaurantId(orderId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found."));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .filter(item -> item.getParentOrderItem() == null)
                .map(this::toOrderItemResponse)
                .toList();

        BigDecimal total = itemResponses.stream()
                .map(OrderItemResponse::getNetSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrderResponse.builder()
                .id(order.getId())
                .restaurantId(order.getRestaurant().getId())
                .tabId(order.getTab().getId())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .total(total)
                .printedAt(order.getPrintedAt())
                .build();
    }

    private OrderItemResponse toOrderItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .observation(item.getObservation())
                .status(item.getStatus())
                .modifiers(toModifierResponses(item))
                .subtotal(item.getSubtotal())
                .discountType(item.getDiscountType())
                .discountValue(item.getDiscountValue())
                .discountAmount(item.getDiscountAmount())
                .discountReason(item.getDiscountReason())
                .discountAppliedBy(item.getDiscountAppliedBy())
                .discountAppliedAt(item.getDiscountAppliedAt())
                .cancelledBy(item.getCancelledBy())
                .cancelledAt(item.getCancelledAt())
                .netSubtotal(item.getNetSubtotal())
                .isComboHeader(item.isComboHeader())
                .children(item.getChildren().stream().map(this::toOrderItemResponse).toList())
                .build();
    }

    private List<OrderItemModifierResponse> toModifierResponses(OrderItem item) {
        return item.getModifiers().stream()
                .map(modifier -> OrderItemModifierResponse.builder()
                        .groupName(modifier.getGroupName())
                        .optionName(modifier.getOptionName())
                        .priceDelta(modifier.getPriceDelta())
                        .build())
                .toList();
    }
}
