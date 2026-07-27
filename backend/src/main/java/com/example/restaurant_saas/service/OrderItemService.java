package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.dto.response.KitchenItemResponse;
import com.example.restaurant_saas.dto.response.OrderItemModifierResponse;
import com.example.restaurant_saas.dto.response.OrderItemResponse;
import com.example.restaurant_saas.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderItemService {

    private static final List<ItemStatus> OPEN_STATUSES = List.of(ItemStatus.PENDING, ItemStatus.PREPARING, ItemStatus.READY);

    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public List<KitchenItemResponse> listKitchenQueue(UUID restaurantId, List<ItemStatus> statusFilter) {
        List<ItemStatus> statuses = (statusFilter == null || statusFilter.isEmpty()) ? OPEN_STATUSES : statusFilter;
        return orderItemRepository.findByOrder_Restaurant_IdAndStatusInOrderByCreatedAtAsc(restaurantId, statuses).stream()
                .map(this::toKitchenResponse)
                .toList();
    }

    @Transactional
    public OrderItemResponse updateStatus(UUID restaurantId, UUID itemId, UserRole currentUserRole, UpdateOrderItemStatusRequest request) {
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
        return toOrderItemResponse(orderItemRepository.save(item));
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
