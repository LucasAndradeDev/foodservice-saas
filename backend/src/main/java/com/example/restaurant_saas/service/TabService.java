package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.domain.enums.TableStatus;
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
    public TabResponse payTab(UUID restaurantId, UUID tabId, PayTabRequest request) {
        Tab tab = findByIdAndRestaurant(restaurantId, tabId);
        if (tab.getStatus() == TabStatus.CLOSED) {
            throw new IllegalArgumentException("Tab is already closed.");
        }
        if (orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(tabId, List.of(ItemStatus.DELIVERED, ItemStatus.CANCELLED))) {
            throw new IllegalStateException("Tab has order items that are not DELIVERED or CANCELLED yet.");
        }

        BigDecimal total = orderItemRepository.sumDeliveredTotalByTabAndRestaurant(tabId, restaurantId);
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
        if (tab.getStatus() == TabStatus.CLOSED) {
            throw new IllegalArgumentException("Tab is already closed.");
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
                .tables(tab.getTables().stream()
                        .map(table -> TabTableSummary.builder()
                                .id(table.getId())
                                .number(table.getNumber())
                                .build())
                        .toList())
                .build();
    }
}
