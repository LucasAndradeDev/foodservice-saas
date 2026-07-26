package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.TableRequest;
import com.example.restaurant_saas.domain.enums.TableRequestType;
import com.example.restaurant_saas.dto.response.TableRequestResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
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

    @Transactional
    public TableRequestResponse createRequest(String slug, UUID tableId, TableRequestType type) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        RestaurantTable table = tableRepository.findByIdAndRestaurantId(tableId, restaurant.getId())
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("Table not found."));

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
