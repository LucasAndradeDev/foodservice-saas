package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.entity.User;
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
import com.example.restaurant_saas.repository.DeliveryDetailsRepository;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderItemService {

    private static final List<ItemStatus> OPEN_STATUSES = List.of(ItemStatus.PENDING, ItemStatus.PREPARING, ItemStatus.READY);

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final TabRepository tabRepository;
    private final RestaurantRepository restaurantRepository;
    private final DeliveryDetailsRepository deliveryDetailsRepository;
    private final WhatsAppService whatsAppService;

    @Transactional(readOnly = true)
    public List<KitchenItemResponse> listKitchenQueue(UUID restaurantId, List<ItemStatus> statusFilter) {
        List<ItemStatus> statuses = (statusFilter == null || statusFilter.isEmpty()) ? OPEN_STATUSES : statusFilter;
        // Keyed by tab id so each delivery order gets its own card in the kitchen queue (grouped by
        // tabId, see KitchenPage#groupByTable) instead of every delivery in the restaurant merging
        // into one - and so the card can show which order it is (customerName).
        Map<UUID, DeliveryDetails> deliveryDetailsByTabId = deliveryDetailsRepository.findByRestaurantId(restaurantId).stream()
                .collect(Collectors.toMap(dd -> dd.getTab().getId(), Function.identity()));
        // Kept out of the queue entirely while unpaid (2026-08-18 decision, docs/DELIVERY.md) - no
        // food prepped for a delivery order that might never get paid. Once the tab closes (paid),
        // it reappears here and flows through PENDING -> ... -> DELIVERED normally.
        Set<UUID> unpaidDeliveryTabIds = deliveryDetailsRepository.findUnpaidTabIdsByRestaurantId(restaurantId);
        return orderItemRepository.findByOrder_Restaurant_IdAndStatusInAndParentOrderItemIsNullOrderByCreatedAtAsc(restaurantId, statuses).stream()
                .filter(item -> !unpaidDeliveryTabIds.contains(item.getOrder().getTab().getId()))
                .map(item -> toKitchenResponse(item, deliveryDetailsByTabId))
                .toList();
    }

    @Transactional
    public OrderItemResponse updateStatus(UUID restaurantId, UUID itemId, UserRole currentUserRole, String actingUserName, UpdateOrderItemStatusRequest request) {
        OrderItem item = orderItemRepository.findByIdAndOrder_Restaurant_Id(itemId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found."));

        if (item.isComboChild()) {
            throw new IllegalArgumentException("This item is part of a combo; update the combo header instead.");
        }

        ItemStatus from = item.getStatus();
        ItemStatus to = request.getStatus();

        if (!isValidTransition(from, to)) {
            throw new IllegalArgumentException("Cannot change status from " + from + " to " + to + ".");
        }
        if (!rolesAllowedFor(from, to).contains(currentUserRole)) {
            throw new IllegalStateException("Role " + currentUserRole + " is not allowed to perform this status change.");
        }
        // Mirrors the kitchen queue's own exclusion (listKitchenQueue) - an unpaid delivery order's
        // items are invisible there, so nothing should be able to move them forward either, even
        // via a direct call. CANCELLED is still allowed (e.g. staff spotting a genuinely dead order).
        if (to != ItemStatus.CANCELLED && isUnpaidDeliveryItem(item)) {
            throw new IllegalStateException("This delivery order hasn't been paid yet.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        applyStatusChange(item, to, actingUserName, now);
        if (item.isComboHeader()) {
            item.getChildren().forEach(child -> applyStatusChange(child, to, actingUserName, now));
            orderItemRepository.saveAll(item.getChildren());
        }
        OrderItem saved = orderItemRepository.save(item);

        if (to == ItemStatus.READY) {
            notifyIfOrderReady(saved);
        }

        return toOrderItemResponse(saved);
    }

    // Fires the "your order is ready" WhatsApp message the moment the last pending/preparing
    // item on the tab flips to READY -- guarded so it only ever fires once per tab.
    private void notifyIfOrderReady(OrderItem item) {
        Tab tab = item.getOrder().getTab();
        if (tab.getCustomerPhone() == null || tab.getReadyNotificationSentAt() != null) {
            return;
        }
        boolean stillPending = orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(
                tab.getId(), List.of(ItemStatus.READY, ItemStatus.DELIVERED, ItemStatus.CANCELLED));
        if (stillPending) {
            return;
        }

        String restaurantName = tab.getRestaurant().getTradeName() != null
                ? tab.getRestaurant().getTradeName()
                : tab.getRestaurant().getName();
        try {
            whatsAppService.sendOrderReadyNotification(tab.getCustomerPhone(), restaurantName);
        } catch (RuntimeException ex) {
            // A delivery failure (malformed number, provider outage, etc.) must never roll back
            // marking the item READY - that already happened in the kitchen and is the real
            // action here; this notification is a courtesy on top of it, not a precondition.
            log.error("Failed to send order-ready WhatsApp notification for tab {}", tab.getId(), ex);
            return;
        }
        tab.setReadyNotificationSentAt(OffsetDateTime.now());
        tabRepository.save(tab);
    }

    private void applyStatusChange(OrderItem item, ItemStatus to, String actingUserName, OffsetDateTime now) {
        item.setStatus(to);
        if (to == ItemStatus.CANCELLED) {
            item.setCancelledBy(actingUserName);
            item.setCancelledAt(now);
        } else if (to == ItemStatus.DELIVERED) {
            item.setDeliveredAt(now);
        }
    }

    @Transactional
    public OrderItemResponse applyDiscount(UUID restaurantId, UUID itemId, String actingUserName, ApplyDiscountRequest request) {
        OrderItem item = orderItemRepository.findByIdAndOrder_Restaurant_Id(itemId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found."));

        if (item.isComboHeader() || item.isComboChild()) {
            throw new IllegalArgumentException("Cannot apply a manual discount to a combo header or its items; it already carries the combo's discount.");
        }
        if (item.getStatus() == ItemStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot discount a cancelled item.");
        }

        // Locked (not the item's lazy tab association): must not race a concurrent
        // registerPayments call freezing billTotal after this check passes, which would apply a
        // discount the already-frozen bill total never accounted for.
        Tab tab = tabRepository.findByIdAndRestaurantIdForUpdate(item.getOrder().getTab().getId(), restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Tab is not open.");
        }
        if (tab.getBillTotal() != null) {
            throw new IllegalStateException("Payment has already started for this tab.");
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

        UUID sourceTabId = items.get(0).getOrder().getTab().getId();
        if (items.stream().anyMatch(item -> !item.getOrder().getTab().getId().equals(sourceTabId))) {
            throw new IllegalArgumentException("All items must belong to the same tab.");
        }
        if (items.stream().anyMatch(item -> item.getStatus() == ItemStatus.CANCELLED)) {
            throw new IllegalArgumentException("Cannot transfer a cancelled item.");
        }
        if (items.stream().anyMatch(item -> item.isComboHeader() || item.isComboChild())) {
            throw new IllegalArgumentException("Cannot transfer a combo item individually; combos must move as a whole order.");
        }
        if (sourceTabId.equals(request.getTargetTabId())) {
            throw new IllegalArgumentException("Cannot transfer items to the same tab.");
        }

        // Locked (not a plain read): items are about to move out of source and into target, which must
        // not race a concurrent registerPayments/applyDiscount call freezing either tab's bill total —
        // otherwise items could leave a tab its frozen total still accounts for, or arrive on one whose
        // total was already computed without them.
        Tab sourceTab = tabRepository.findByIdAndRestaurantIdForUpdate(sourceTabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (sourceTab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Source tab is not open.");
        }
        if (sourceTab.getBillTotal() != null) {
            throw new IllegalStateException("Payment has already started for the source tab.");
        }

        Tab targetTab = tabRepository.findByIdAndRestaurantIdForUpdate(request.getTargetTabId(), restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Target tab not found."));
        if (targetTab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Target tab is not open.");
        }
        if (targetTab.getBillTotal() != null) {
            throw new IllegalStateException("Payment has already started for the target tab.");
        }

        // Carries over the waiter who originally created the item so waiter-performance reporting still
        // credits them after the move; if the batch spans items from different original creators, the
        // first one wins (transfers are validated to be within a single tab, not a single order).
        User originalCreatedBy = items.get(0).getOrder().getCreatedBy();

        OffsetDateTime now = OffsetDateTime.now();
        Order transferOrder = orderRepository.save(Order.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .tab(targetTab)
                .createdBy(originalCreatedBy)
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

    private boolean isUnpaidDeliveryItem(OrderItem item) {
        Tab tab = item.getOrder().getTab();
        return deliveryDetailsRepository.findByTab_Id(tab.getId()).isPresent() && tab.getStatus() != TabStatus.CLOSED;
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

    private KitchenItemResponse toKitchenResponse(OrderItem item, Map<UUID, DeliveryDetails> deliveryDetailsByTabId) {
        Order order = item.getOrder();
        UUID tabId = order.getTab().getId();
        List<Integer> tableNumbers = order.getTab().getTables().stream()
                .map(RestaurantTable::getNumber)
                .toList();
        DeliveryDetails deliveryDetails = deliveryDetailsByTabId.get(tabId);

        return KitchenItemResponse.builder()
                .id(item.getId())
                .orderId(order.getId())
                .tabId(tabId)
                .tableNumbers(tableNumbers)
                .isDelivery(deliveryDetails != null)
                .deliveryCustomerName(deliveryDetails != null ? deliveryDetails.getCustomerName() : null)
                .deliveryAddress(deliveryDetails != null ? formatDeliveryAddress(deliveryDetails) : null)
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .observation(item.getObservation())
                .modifiers(toModifierResponses(item))
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
                .isComboHeader(item.isComboHeader())
                .children(item.getChildren().stream().map(child -> toKitchenResponse(child, deliveryDetailsByTabId)).toList())
                .build();
    }

    // Same format as the staff-facing delivery list (DeliveryPage.tsx) and the customer-facing
    // status page - keeping it identical means the kitchen card's address genuinely matches what
    // staff already sees elsewhere, not just something that looks similar.
    private String formatDeliveryAddress(DeliveryDetails deliveryDetails) {
        return deliveryDetails.getStreet() + ", " + deliveryDetails.getNumber() + " - " + deliveryDetails.getNeighborhood();
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
