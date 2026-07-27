package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.domain.enums.TableStatus;
import com.example.restaurant_saas.dto.request.AddTableToTabRequest;
import com.example.restaurant_saas.dto.request.ApplyDiscountRequest;
import com.example.restaurant_saas.dto.request.MergeTabRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PayTabRequest;
import com.example.restaurant_saas.dto.response.TabResponse;
import com.example.restaurant_saas.dto.response.TabTableSummary;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TabService {

    private final TabRepository tabRepository;
    private final RestaurantTableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<TabResponse> listTabs(UUID restaurantId, TabStatus statusFilter) {
        return tabRepository.findByRestaurantId(restaurantId).stream()
                .filter(tab -> statusFilter == null || statusFilter.equals(tab.getStatus()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TabResponse getTab(UUID restaurantId, UUID tabId) {
        return toResponse(findByIdAndRestaurant(restaurantId, tabId));
    }

    @Transactional
    public TabResponse openTab(UUID restaurantId, OpenTabRequest request) {
        List<UUID> distinctIds = request.getTableIds().stream().distinct().toList();

        List<RestaurantTable> tables = tableRepository.findByIdInAndRestaurantId(distinctIds, restaurantId);
        if (tables.size() != distinctIds.size()) {
            throw new IllegalArgumentException("One or more tables were not found in this restaurant.");
        }

        for (RestaurantTable table : tables) {
            if (!Boolean.TRUE.equals(table.getActive())) {
                throw new IllegalStateException("Table " + table.getNumber() + " is not active.");
            }
            if (table.getStatus() != TableStatus.FREE) {
                throw new IllegalStateException("Table " + table.getNumber() + " is not free.");
            }
        }

        Tab tab = Tab.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .status(TabStatus.OPEN)
                .openedAt(OffsetDateTime.now())
                .tables(tables)
                .build();
        tab = tabRepository.save(tab);

        tables.forEach(table -> table.setStatus(TableStatus.OCCUPIED));
        tableRepository.saveAll(tables);

        return toResponse(tab);
    }

    @Transactional
    public Tab openOrGetTabForTable(UUID restaurantId, UUID tableId) {
        RestaurantTable table = tableRepository.findByIdAndRestaurantIdForUpdate(tableId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found."));
        if (!Boolean.TRUE.equals(table.getActive())) {
            throw new IllegalStateException("Table is not active.");
        }

        Optional<Tab> openTab = tabRepository.findOpenTabByRestaurantIdAndTableId(restaurantId, tableId);
        if (openTab.isPresent()) {
            return openTab.get();
        }

        if (table.getStatus() != TableStatus.FREE) {
            throw new IllegalStateException("Table is not available for self-ordering right now. Please call a waiter.");
        }

        Tab tab = Tab.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .status(TabStatus.OPEN)
                .openedAt(OffsetDateTime.now())
                .tables(List.of(table))
                .build();
        tab = tabRepository.save(tab);

        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);

        return tab;
    }

    @Transactional
    public TabResponse addTable(UUID restaurantId, UUID tabId, AddTableToTabRequest request) {
        Tab tab = findByIdAndRestaurant(restaurantId, tabId);
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Tab is not open.");
        }

        RestaurantTable table = tableRepository.findByIdAndRestaurantIdForUpdate(request.getTableId(), restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found."));
        if (!Boolean.TRUE.equals(table.getActive())) {
            throw new IllegalStateException("Table is not active.");
        }
        if (table.getStatus() != TableStatus.FREE) {
            throw new IllegalStateException("Table " + table.getNumber() + " is not free.");
        }

        tab.getTables().add(table);
        tabRepository.save(tab);

        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);

        return toResponse(tab);
    }

    @Transactional
    public TabResponse mergeTab(UUID restaurantId, UUID targetTabId, MergeTabRequest request) {
        if (targetTabId.equals(request.getSourceTabId())) {
            throw new IllegalArgumentException("A tab cannot be merged into itself.");
        }

        Tab target = findByIdAndRestaurant(restaurantId, targetTabId);
        if (target.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Target tab is not open.");
        }

        Tab source = findByIdAndRestaurant(restaurantId, request.getSourceTabId());
        if (source.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Source tab is not open.");
        }

        List<Order> sourceOrders = orderRepository.findByTabIdAndRestaurantId(source.getId(), restaurantId);
        sourceOrders.forEach(order -> {
            order.setMergedFromTabId(source.getId());
            order.setTab(target);
        });
        orderRepository.saveAll(sourceOrders);

        // Source keeps its own tables (not cleared) so a short-lived "undo merge" can restore them;
        // it's excluded from OPEN listings anyway since its status is MERGED.
        target.getTables().addAll(source.getTables());

        source.setStatus(TabStatus.MERGED);
        source.setClosedAt(OffsetDateTime.now());
        tabRepository.save(source);
        Tab savedTarget = tabRepository.save(target);

        return toResponse(savedTarget);
    }

    @Transactional
    public TabResponse unmergeTab(UUID restaurantId, UUID targetTabId, UUID sourceTabId) {
        Tab target = findByIdAndRestaurant(restaurantId, targetTabId);
        if (target.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Target tab is not open.");
        }

        Tab source = findByIdAndRestaurant(restaurantId, sourceTabId);
        if (source.getStatus() != TabStatus.MERGED) {
            throw new IllegalArgumentException("Source tab was not merged into this tab.");
        }

        List<Order> movedOrders = orderRepository.findByTabIdAndMergedFromTabId(targetTabId, sourceTabId);
        movedOrders.forEach(order -> {
            order.setTab(source);
            order.setMergedFromTabId(null);
        });
        orderRepository.saveAll(movedOrders);

        target.getTables().removeAll(source.getTables());
        Tab savedTarget = tabRepository.save(target);

        source.setStatus(TabStatus.OPEN);
        source.setClosedAt(null);
        tabRepository.save(source);

        return toResponse(savedTarget);
    }

    @Transactional
    public TabResponse payTab(UUID restaurantId, UUID tabId, PayTabRequest request) {
        Tab tab = findByIdAndRestaurant(restaurantId, tabId);
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Tab is not open.");
        }
        if (orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(tabId, List.of(ItemStatus.DELIVERED, ItemStatus.CANCELLED))) {
            throw new IllegalStateException("Tab has order items that are not DELIVERED or CANCELLED yet.");
        }

        BigDecimal itemsTotal = computeItemsTotal(restaurantId, tabId);
        BigDecimal total = itemsTotal.subtract(tab.getDiscountAmount(itemsTotal));
        if (total.compareTo(request.getPaidAmount()) != 0) {
            throw new IllegalArgumentException("Paid amount does not match the tab total.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        tab.setStatus(TabStatus.CLOSED);
        tab.setClosedAt(now);
        tab.setPaymentMethod(request.getPaymentMethod());
        tab.setPaidAmount(request.getPaidAmount());
        tab.setPaidAt(now);
        tab = tabRepository.save(tab);

        tab.getTables().forEach(table -> table.setStatus(TableStatus.FREE));
        tableRepository.saveAll(tab.getTables());

        return toResponse(tab);
    }

    @Transactional
    public TabResponse cancelTab(UUID restaurantId, UUID tabId) {
        Tab tab = findByIdAndRestaurant(restaurantId, tabId);
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Tab is not open.");
        }
        if (orderRepository.existsByTabId(tabId)) {
            throw new IllegalStateException("Cannot cancel a tab that already has orders.");
        }

        tab.setStatus(TabStatus.CLOSED);
        tab.setClosedAt(OffsetDateTime.now());
        tab = tabRepository.save(tab);

        tab.getTables().forEach(table -> table.setStatus(TableStatus.FREE));
        tableRepository.saveAll(tab.getTables());

        return toResponse(tab);
    }

    @Transactional
    public TabResponse markReceiptPrinted(UUID restaurantId, UUID tabId) {
        Tab tab = findByIdAndRestaurant(restaurantId, tabId);
        tab.setReceiptPrintedAt(OffsetDateTime.now());
        return toResponse(tabRepository.save(tab));
    }

    @Transactional
    public TabResponse applyDiscount(UUID restaurantId, UUID tabId, String actingUserName, ApplyDiscountRequest request) {
        Tab tab = findByIdAndRestaurant(restaurantId, tabId);
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Tab is not open.");
        }

        if (request.getDiscountType() == null) {
            tab.setDiscountType(null);
            tab.setDiscountValue(null);
            tab.setDiscountReason(null);
            tab.setDiscountAppliedBy(null);
            tab.setDiscountAppliedAt(null);
        } else {
            BigDecimal itemsTotal = computeItemsTotal(restaurantId, tabId);
            validateDiscountValue(request.getDiscountType(), request.getDiscountValue(), itemsTotal);
            tab.setDiscountType(request.getDiscountType());
            tab.setDiscountValue(request.getDiscountValue());
            tab.setDiscountReason(request.getReason());
            tab.setDiscountAppliedBy(actingUserName);
            tab.setDiscountAppliedAt(OffsetDateTime.now());
        }

        return toResponse(tabRepository.save(tab));
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

    private BigDecimal computeItemsTotal(UUID restaurantId, UUID tabId) {
        return orderItemRepository
                .findByOrder_Tab_IdAndOrder_Restaurant_IdAndStatus(tabId, restaurantId, ItemStatus.DELIVERED).stream()
                .map(OrderItem::getNetSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Tab findByIdAndRestaurant(UUID restaurantId, UUID tabId) {
        return tabRepository.findByIdAndRestaurantId(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
    }

    private TabResponse toResponse(Tab tab) {
        return TabResponse.builder()
                .id(tab.getId())
                .restaurantId(tab.getRestaurant().getId())
                .status(tab.getStatus())
                .openedAt(tab.getOpenedAt())
                .closedAt(tab.getClosedAt())
                .paymentMethod(tab.getPaymentMethod())
                .paidAmount(tab.getPaidAmount())
                .paidAt(tab.getPaidAt())
                .receiptPrintedAt(tab.getReceiptPrintedAt())
                .discountType(tab.getDiscountType())
                .discountValue(tab.getDiscountValue())
                .discountReason(tab.getDiscountReason())
                .discountAppliedBy(tab.getDiscountAppliedBy())
                .discountAppliedAt(tab.getDiscountAppliedAt())
                .tables(tab.getTables().stream()
                        .map(table -> TabTableSummary.builder()
                                .id(table.getId())
                                .number(table.getNumber())
                                .build())
                        .toList())
                .build();
    }
}
