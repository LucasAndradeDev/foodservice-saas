package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.NavNotificationSeen;
import com.example.restaurant_saas.domain.entity.TableRequest;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.NavSection;
import com.example.restaurant_saas.domain.enums.TableRequestType;
import com.example.restaurant_saas.dto.response.NavNotificationStatusResponse;
import com.example.restaurant_saas.repository.NavNotificationSeenRepository;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.TableRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NavNotificationService {

    private final NavNotificationSeenRepository navNotificationSeenRepository;
    private final OrderItemRepository orderItemRepository;
    private final TableRequestRepository tableRequestRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public NavNotificationStatusResponse getStatus(UUID restaurantId) {
        OffsetDateTime kitchenSeen = lastSeen(restaurantId, NavSection.KITCHEN);
        OffsetDateTime tablesSeen = lastSeen(restaurantId, NavSection.TABLES);
        OffsetDateTime checkoutSeen = lastSeen(restaurantId, NavSection.CHECKOUT);

        boolean kitchen = orderItemRepository.existsByOrder_Restaurant_IdAndStatusAndCreatedAtAfter(
                restaurantId, ItemStatus.PENDING, kitchenSeen);

        List<TableRequest> pendingRequests = tableRequestRepository.findByRestaurantIdAndAcknowledgedAtIsNull(restaurantId);

        boolean tablesHasReadyItem = orderItemRepository.existsByOrder_Restaurant_IdAndStatusAndUpdatedAtAfter(
                restaurantId, ItemStatus.READY, tablesSeen);
        boolean tablesHasPendingRequest = pendingRequests.stream()
                .anyMatch(request -> request.getRequestedAt().isAfter(tablesSeen));
        boolean checkoutHasPendingBill = pendingRequests.stream()
                .anyMatch(request -> request.getType() == TableRequestType.REQUEST_BILL && request.getRequestedAt().isAfter(checkoutSeen));

        return NavNotificationStatusResponse.builder()
                .kitchen(kitchen)
                .tables(tablesHasReadyItem || tablesHasPendingRequest)
                .checkout(checkoutHasPendingBill)
                .build();
    }

    @Transactional
    public void markSeen(UUID restaurantId, NavSection section) {
        NavNotificationSeen seen = navNotificationSeenRepository.findByRestaurant_IdAndSection(restaurantId, section)
                .orElseGet(() -> NavNotificationSeen.builder()
                        .restaurant(restaurantRepository.getReferenceById(restaurantId))
                        .section(section)
                        .build());
        seen.setLastSeenAt(OffsetDateTime.now());
        navNotificationSeenRepository.save(seen);
    }

    private OffsetDateTime lastSeen(UUID restaurantId, NavSection section) {
        return navNotificationSeenRepository.findByRestaurant_IdAndSection(restaurantId, section)
                .map(NavNotificationSeen::getLastSeenAt)
                .orElse(OffsetDateTime.MIN);
    }
}
