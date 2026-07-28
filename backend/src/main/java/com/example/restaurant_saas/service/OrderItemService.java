package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.ApplyDiscountRequest;
import com.example.restaurant_saas.dto.request.TransferItemsRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.dto.response.KitchenItemResponse;
import com.example.restaurant_saas.dto.response.OrderItemModifierResponse;
import com.example.restaurant_saas.dto.response.OrderItemResponse;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderItemService {

    private static final List<ItemStatus> OPEN_STATUSES = List.of(ItemStatus.PENDING, ItemStatus.PREPARING, ItemStatus.READY);

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final TabRepository tabRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<KitchenItemResponse> listKitchenQueue(UUID restaurantId, List<ItemStatus> statusFilter) {
        List<ItemStatus> statuses = (statusFilter == null || statusFilter.isEmpty()) ? OPEN_STATUSES : statusFilter;
        return orderItemRepository.findByOrder_Restaurant_IdAndStatusInOrderByCreatedAtAsc(restaurantId, statuses).stream()
                .map(this::toKitchenResponse)
                .toList();
    }

    @Transactional
    public OrderItemResponse updateStatus(UUID restaurantId, UUID itemId, UserRole currentUserRole, String actingUserName, UpdateOrderItemStatusRequest request) {
        OrderItem item = orderItemRepository.findByIdAndOrder_Restaurant_Id(itemId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found."));

        ItemStatus from = item.getStatus();
        ItemStatus to = request.getStatus();

        if (!isValidTransition(from, to)) {
            throw new IllegalArgumentException("Cannot change status from " + from + " to " + to + ".");
        }
        if (!rolesAllowedFor(from, to).contains(currentUserRole)) {
            throw new IllegalStateException("Role " + currentUserRole + " is not allowed to perform this status change.");
        }

        item.setStatus(to);
        if (to == ItemStatus.CANCELLED) {
            item.setCancelledBy(actingUserName);
            item.setCancelledAt(OffsetDateTime.now());
        } else if (to == ItemStatus.DELIVERED) {
            item.setDeliveredAt(OffsetDateTime.now());
        }
        return toOrderItemResponse(orderItemRepository.save(item));
    }

    @Transactional
    public OrderItemResponse applyDiscount(UUID restaurantId, UUID itemId, String actingUserName, ApplyDiscountRequest request) {
        OrderItem item = orderItemRepository.findByIdAndOrder_Restaurant_Id(itemId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found."));

        if (item.getStatus() == ItemStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot discount a cancelled item.");
        }
        if (item.getOrder().getTab().getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Tab is not open.");
        }

        if (request.getDiscountType() == null) {
            item.setDiscountType(null);
            item.setDiscountValue(null);
            item.setDiscountReason(null);
            item.setDiscountAppliedBy(null);
            item.setDiscountAppliedAt(null);
        } else {
            validateDiscountValue(request.getDiscountType(), request.getDiscountValue(), item.getSubtotal());
            item.setDiscountType(request.getDiscountType());
            item.setDiscountValue(request.getDiscountValue());
            item.setDiscountReason(request.getReason());
            item.setDiscountAppliedBy(actingUserName);
            item.setDiscountAppliedAt(OffsetDateTime.now());
        }

        return toOrderItemResponse(orderItemRepository.save(item));
    }

    @Transactional
    public List<OrderItemResponse> transferItems(UUID restaurantId, String actingUserName, TransferItemsRequest request) {
        List<OrderItem> items = request.getItemIds().stream()
                .map(itemId -> orderItemRepository.findByIdAndOrder_Restaurant_Id(itemId, restaurantId)
                        .orElseThrow(() -> new IllegalArgumentException("Order item not found.")))
                .toList();

        Tab sourceTab = items.get(0).getOrder().getTab();
        if (items.stream().anyMatch(item -> !item.getOrder().getTab().getId().equals(sourceTab.getId()))) {
            throw new IllegalArgumentException("All items must belong to the same tab.");
        }
        if (items.stream().anyMatch(item -> item.getStatus() == ItemStatus.CANCELLED)) {
            throw new IllegalArgumentException("Cannot transfer a cancelled item.");
        }
        if (sourceTab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Source tab is not open.");
        }
        if (sourceTab.getId().equals(request.getTargetTabId())) {
            throw new IllegalArgumentException("Cannot transfer items to the same tab.");
        }

        Tab targetTab = tabRepository.findByIdAndRestaurantId(request.getTargetTabId(), restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Target tab not found."));
        if (targetTab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Target tab is not open.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Order transferOrder = orderRepository.save(Order.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .tab(targetTab)
                .transferredFromTabId(sourceTab.getId())
                .transferredBy(actingUserName)
                .transferredAt(now)
                .build());

        items.forEach(item -> item.setOrder(transferOrder));
        orderItemRepository.saveAll(items);

        return items.stream().map(this::toOrderItemResponse).toList();
    }

    private void validateDiscountValue(DiscountType type, BigDecimal value, BigDecimal baseAmount) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than zero.");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100.");
        }
        if (type == DiscountType.FIXED && value.compareTo(baseAmount) > 0) {
            throw new IllegalArgumentException("Discount amount cannot exceed the amount being discounted.");
        }
    }

    private boolean isValidTransition(ItemStatus from, ItemStatus to) {
        return switch (from) {
            case PENDING -> to == ItemStatus.PREPARING || to == ItemStatus.CANCELLED;
            case PREPARING -> to == ItemStatus.READY || to == ItemStatus.CANCELLED;
            case READY -> to == ItemStatus.DELIVERED || to == ItemStatus.CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    private Set<UserRole> rolesAllowedFor(ItemStatus from, ItemStatus to) {
        if (to == ItemStatus.CANCELLED) {
            return EnumSet.of(UserRole.OWNER, UserRole.MANAGER, UserRole.WAITER, UserRole.CASHIER, UserRole.KITCHEN);
        }
        return switch (from) {
            case PENDING, PREPARING -> EnumSet.of(UserRole.OWNER, UserRole.MANAGER, UserRole.KITCHEN);
            case READY -> EnumSet.of(UserRole.OWNER, UserRole.MANAGER, UserRole.WAITER, UserRole.CASHIER);
            default -> EnumSet.noneOf(UserRole.class);
        };
    }

    private KitchenItemResponse toKitchenResponse(OrderItem item) {
        Order order = item.getOrder();
        List<Integer> tableNumbers = order.getTab().getTables().stream()
                .map(RestaurantTable::getNumber)
                .toList();

        return KitchenItemResponse.builder()
                .id(item.getId())
                .orderId(order.getId())
                .tableNumbers(tableNumbers)
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .observation(item.getObservation())
                .modifiers(toModifierResponses(item))
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
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
