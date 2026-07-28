package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.OrderItemModifier;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.entity.ProductModifierGroup;
import com.example.restaurant_saas.domain.entity.ProductModifierOption;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.ModifierSelectionType;
import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.response.OrderItemModifierResponse;
import com.example.restaurant_saas.dto.response.OrderItemResponse;
import com.example.restaurant_saas.dto.response.OrderResponse;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.ProductModifierGroupRepository;
import com.example.restaurant_saas.repository.ProductModifierOptionRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    private final ProductModifierGroupRepository modifierGroupRepository;
    private final ProductModifierOptionRepository modifierOptionRepository;

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
    public OrderResponse createOrder(UUID restaurantId, UUID tabId, CreateOrderRequest request) {
        Tab tab = tabRepository.findByIdAndRestaurantId(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalStateException("Tab is not open.");
        }

        Order order = Order.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .tab(tab)
                .build();

        List<OrderItem> items = request.getItems().stream()
                .map(itemRequest -> toOrderItem(restaurantId, order, itemRequest))
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

    private OrderItem toOrderItem(UUID restaurantId, Order order, CreateOrderItemRequest itemRequest) {
        Product product = productRepository.findByIdAndRestaurantId(itemRequest.getProductId(), restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalStateException("Product " + product.getName() + " is not active.");
        }

        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .quantity(itemRequest.getQuantity())
                .unitPrice(product.getPrice())
                .observation(itemRequest.getObservation())
                .status(ItemStatus.PENDING)
                .build();

        List<OrderItemModifier> modifiers = resolveModifiers(restaurantId, product, itemRequest.getSelectedOptionIds());
        modifiers.forEach(modifier -> modifier.setOrderItem(item));
        item.setModifiers(modifiers);

        return item;
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
