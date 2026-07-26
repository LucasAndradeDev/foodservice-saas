package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.TableRequest;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.TableRequestType;
import com.example.restaurant_saas.dto.response.TableRequestResponse;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import com.example.restaurant_saas.repository.TabRepository;
import com.example.restaurant_saas.repository.TableRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicTableRequestService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantTableRepository tableRepository;
    private final TableRequestRepository tableRequestRepository;
    private final TabRepository tabRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public TableRequestResponse createRequest(String slug, UUID tableId, TableRequestType type) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        RestaurantTable table = tableRepository.findByIdAndRestaurantId(tableId, restaurant.getId())
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("Table not found."));

        if (type == TableRequestType.REQUEST_BILL) {
            boolean hasDeliveredItems = tabRepository.findOpenTabByRestaurantIdAndTableId(restaurant.getId(), tableId)
                    .map(tab -> orderItemRepository.existsByOrder_Tab_IdAndStatus(tab.getId(), ItemStatus.DELIVERED))
                    .orElse(false);
            if (!hasDeliveredItems) {
                throw new IllegalArgumentException("Cannot request the bill for a table with no delivered items yet.");
            }
        }

        TableRequest pending = tableRequestRepository
                .findByTableIdAndTypeAndAcknowledgedAtIsNull(tableId, type)
                .orElse(null);
        if (pending != null) {
            return toResponse(pending);
        }

        TableRequest request = TableRequest.builder()
                .restaurant(restaurant)
                .table(table)
                .type(type)
                .build();

        return toResponse(tableRequestRepository.save(request));
    }

    private TableRequestResponse toResponse(TableRequest request) {
        return TableRequestResponse.builder()
                .id(request.getId())
                .tableId(request.getTable().getId())
                .type(request.getType())
                .requestedAt(request.getRequestedAt())
                .acknowledgedAt(request.getAcknowledgedAt())
                .build();
    }
}
