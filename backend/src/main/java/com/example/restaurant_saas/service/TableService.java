package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.enums.TableStatus;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.CreateTablesBulkRequest;
import com.example.restaurant_saas.dto.request.UpdateTableRequest;
import com.example.restaurant_saas.dto.request.UpdateTableStatusRequest;
import com.example.restaurant_saas.dto.response.TableResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class TableService {

    private final RestaurantTableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<TableResponse> listTables(UUID restaurantId, TableStatus statusFilter, Boolean activeFilter) {
        return tableRepository.findByRestaurantId(restaurantId).stream()
                .filter(table -> statusFilter == null || statusFilter.equals(table.getStatus()))
                .filter(table -> activeFilter == null || activeFilter.equals(table.getActive()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TableResponse getTable(UUID restaurantId, UUID tableId) {
        return toResponse(findByIdAndRestaurant(restaurantId, tableId));
    }

    @Transactional
    public TableResponse createTable(UUID restaurantId, CreateTableRequest request) {
        Integer number = request.getNumber();
        if (number == null) {
            number = nextAvailableNumber(restaurantId);
        } else if (tableRepository.existsByRestaurantIdAndNumber(restaurantId, number)) {
            throw new IllegalArgumentException("A table with this number already exists.");
        }

        RestaurantTable table = RestaurantTable.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .number(number)
                .status(TableStatus.FREE)
                .active(true)
                .build();

        return toResponse(tableRepository.save(table));
    }

    @Transactional
    public List<TableResponse> createTablesBulk(UUID restaurantId, CreateTablesBulkRequest request) {
        int startNumber = nextAvailableNumber(restaurantId);
        Restaurant restaurant = restaurantRepository.getReferenceById(restaurantId);

        List<RestaurantTable> tables = IntStream.range(0, request.getQuantity())
                .mapToObj(offset -> RestaurantTable.builder()
                        .restaurant(restaurant)
                        .number(startNumber + offset)
                        .status(TableStatus.FREE)
                        .active(true)
                        .build())
                .toList();

        return tableRepository.saveAll(tables).stream()
                .map(this::toResponse)
                .toList();
    }

    private int nextAvailableNumber(UUID restaurantId) {
        return tableRepository.findMaxNumberByRestaurantId(restaurantId) + 1;
    }

    @Transactional
    public TableResponse updateTable(UUID restaurantId, UUID tableId, UpdateTableRequest request) {
        RestaurantTable table = findByIdAndRestaurant(restaurantId, tableId);

        if (request.getNumber() != null) {
            if (tableRepository.existsByRestaurantIdAndNumberAndIdNot(restaurantId, request.getNumber(), tableId)) {
                throw new IllegalArgumentException("A table with this number already exists.");
            }
            table.setNumber(request.getNumber());
        }
        if (request.getActive() != null) {
            table.setActive(request.getActive());
        }

        return toResponse(tableRepository.save(table));
    }

    @Transactional
    public TableResponse updateTableStatus(UUID restaurantId, UUID tableId, UpdateTableStatusRequest request) {
        RestaurantTable table = findByIdAndRestaurant(restaurantId, tableId);
        table.setStatus(request.getStatus());
        return toResponse(tableRepository.save(table));
    }

    private RestaurantTable findByIdAndRestaurant(UUID restaurantId, UUID tableId) {
        return tableRepository.findByIdAndRestaurantId(tableId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found."));
    }

    private TableResponse toResponse(RestaurantTable table) {
        return TableResponse.builder()
                .id(table.getId())
                .restaurantId(table.getRestaurant().getId())
                .number(table.getNumber())
                .status(table.getStatus())
                .active(table.getActive())
                .build();
    }
}
