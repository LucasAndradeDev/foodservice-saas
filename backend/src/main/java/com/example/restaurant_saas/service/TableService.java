package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.DiningArea;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.enums.TableStatus;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.CreateTablesBulkRequest;
import com.example.restaurant_saas.dto.request.UpdateTableRequest;
import com.example.restaurant_saas.dto.request.UpdateTableStatusRequest;
import com.example.restaurant_saas.dto.response.TableResponse;
import com.example.restaurant_saas.repository.DiningAreaRepository;
import com.example.restaurant_saas.repository.ReservationRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class TableService {

    private final RestaurantTableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final DiningAreaRepository diningAreaRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public List<TableResponse> listTables(UUID restaurantId, TableStatus statusFilter, Boolean activeFilter) {
        Set<UUID> blockedTableIds = currentlyBlockedTableIds(restaurantId);
        return tableRepository.findByRestaurantId(restaurantId).stream()
                .map(table -> toResponse(table, blockedTableIds))
                .filter(response -> statusFilter == null || statusFilter.equals(response.getStatus()))
                .filter(response -> activeFilter == null || activeFilter.equals(response.getActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TableResponse getTable(UUID restaurantId, UUID tableId) {
        RestaurantTable table = findByIdAndRestaurant(restaurantId, tableId);
        return toResponse(table, currentlyBlockedTableIds(restaurantId));
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
                .area(resolveArea(restaurantId, request.getAreaId()))
                .number(number)
                .capacity(request.getCapacity() != null ? request.getCapacity() : 4)
                .status(TableStatus.FREE)
                .active(true)
                .build();

        return toResponse(tableRepository.save(table), Set.of());
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
                .map(table -> toResponse(table, Set.of()))
                .toList();
    }

    private int nextAvailableNumber(UUID restaurantId) {
        int candidate = 1;
        for (int number : tableRepository.findAllNumbersByRestaurantId(restaurantId)) {
            if (number > candidate) {
                break;
            }
            if (number == candidate) {
                candidate++;
            }
        }
        return candidate;
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
        if (request.getCapacity() != null) {
            table.setCapacity(request.getCapacity());
        }
        if (request.getActive() != null) {
            table.setActive(request.getActive());
        }
        if (Boolean.TRUE.equals(request.getClearArea())) {
            table.setArea(null);
        } else if (request.getAreaId() != null) {
            table.setArea(resolveArea(restaurantId, request.getAreaId()));
        }

        return toResponse(tableRepository.save(table), currentlyBlockedTableIds(restaurantId));
    }

    private DiningArea resolveArea(UUID restaurantId, UUID areaId) {
        if (areaId == null) {
            return null;
        }
        return diningAreaRepository.findByIdAndRestaurantId(areaId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Dining area not found."));
    }

    @Transactional
    public TableResponse updateTableStatus(UUID restaurantId, UUID tableId, UpdateTableStatusRequest request) {
        if (request.getStatus() == TableStatus.RESERVED) {
            throw new IllegalArgumentException("RESERVED is computed automatically and cannot be set directly.");
        }
        RestaurantTable table = findByIdAndRestaurant(restaurantId, tableId);
        table.setStatus(request.getStatus());
        return toResponse(tableRepository.save(table), currentlyBlockedTableIds(restaurantId));
    }

    @Transactional
    public void deleteTable(UUID restaurantId, UUID tableId) {
        RestaurantTable table = findByIdAndRestaurant(restaurantId, tableId);
        if (table.getStatus() != TableStatus.FREE) {
            throw new IllegalStateException("Cannot delete a table that is not FREE.");
        }
        tableRepository.delete(table);
    }

    private RestaurantTable findByIdAndRestaurant(UUID restaurantId, UUID tableId) {
        return tableRepository.findByIdAndRestaurantId(tableId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found."));
    }

    // No persisted RESERVED status and no cron job to clear it -- same "compute live, self-expires"
    // approach as TabService.assertTablesNotReserved and ReservationService's auto-assign. A FREE table
    // whose id shows up here is inside a SCHEDULED reservation's block window right now.
    private Set<UUID> currentlyBlockedTableIds(UUID restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found."));
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowStart = now.minusMinutes(restaurant.getReservationBlockAfterMinutes());
        OffsetDateTime windowEnd = now.plusMinutes(restaurant.getReservationBlockBeforeMinutes());
        return new HashSet<>(reservationRepository.findBlockedTableIds(restaurantId, windowStart, windowEnd));
    }

    private TableResponse toResponse(RestaurantTable table, Set<UUID> blockedTableIds) {
        TableStatus effectiveStatus = table.getStatus() == TableStatus.FREE && blockedTableIds.contains(table.getId())
                ? TableStatus.RESERVED
                : table.getStatus();
        return TableResponse.builder()
                .id(table.getId())
                .restaurantId(table.getRestaurant().getId())
                .number(table.getNumber())
                .capacity(table.getCapacity())
                .status(effectiveStatus)
                .active(table.getActive())
                .areaId(table.getArea() != null ? table.getArea().getId() : null)
                .build();
    }
}
