package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
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
import org.springframework.dao.DataIntegrityViolationException;
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
    private final TenantActivator tenantActivator;
    private final TableRequestInsertService tableRequestInsertService;

    @Transactional
    public TableRequestResponse createRequest(String slug, UUID tableId, TableRequestType type) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        tenantActivator.activate(restaurant.getId());
        try {
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

            try {
                // Delegates to a separate REQUIRES_NEW transaction (own pooled connection) - not a
                // plain save() in this one. Confirmed live under real concurrency: a bare try/catch
                // around save() in this same transaction turned every losing request into a raw
                // 500, because Postgres aborts the *whole* transaction on a constraint violation,
                // and the fallback read below then ran on that same now-aborted connection. See
                // TableRequestInsertService's javadoc for the full explanation.
                return toResponse(tableRequestInsertService.insert(request));
            } catch (DataIntegrityViolationException e) {
                // The read above and this insert aren't atomic - a concurrent duplicate click/retry
                // can pass the same null-pending check before either commits (finding #13, 2026-09-07
                // review). idx_table_requests_pending_unique (V76) makes the database the tiebreaker:
                // the loser lands here and returns the winner's row instead of erroring - idempotent
                // from the caller's perspective, same result as if it had read second.
                return tableRequestRepository.findByTableIdAndTypeAndAcknowledgedAtIsNull(tableId, type)
                        .map(this::toResponse)
                        .orElseThrow(() -> e);
            }
        } finally {
            tenantActivator.deactivate();
        }
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
