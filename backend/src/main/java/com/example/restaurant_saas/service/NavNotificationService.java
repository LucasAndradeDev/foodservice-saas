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
import com.example.restaurant_saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NavNotificationService {

    /**
     * Fallback "last seen" instant for a section nobody has visited yet. OffsetDateTime.MIN
     * overflows when the Postgres driver converts it to a timestamptz bind parameter, so we use
     * a safely-in-range date instead — any real event will always be after it.
     */
    private static final OffsetDateTime NEVER_SEEN = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final NavNotificationSeenRepository navNotificationSeenRepository;
    private final OrderItemRepository orderItemRepository;
    private final TableRequestRepository tableRequestRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public NavNotificationStatusResponse getStatus(UUID restaurantId, UUID userId) {
        OffsetDateTime kitchenSeen = lastSeen(restaurantId, userId, NavSection.KITCHEN);
        OffsetDateTime tablesSeen = lastSeen(restaurantId, userId, NavSection.TABLES);
        OffsetDateTime checkoutSeen = lastSeen(restaurantId, userId, NavSection.CHECKOUT);

        boolean kitchen = orderItemRepository.existsByOrder_Restaurant_IdAndStatusAndCreatedAtAfter(
                restaurantId, ItemStatus.PENDING, kitchenSeen);

        List<TableRequest> pendingRequests = tableRequestRepository.findByRestaurantIdAndAcknowledgedAtIsNull(restaurantId);

        // Each flag below maps to exactly one real-world event, so the frontend can show a toast
        // message that actually matches what happened instead of a single message covering
        // several unrelated triggers (e.g. "table calling" firing for a ready-to-deliver item).
        boolean tablesItemReady = orderItemRepository.existsByOrder_Restaurant_IdAndStatusAndUpdatedAtAfter(
                restaurantId, ItemStatus.READY, tablesSeen);
        boolean tablesCallWaiter = pendingRequests.stream()
                .anyMatch(request -> request.getType() == TableRequestType.CALL_WAITER && request.getRequestedAt().isAfter(tablesSeen));
        boolean tablesRequestBill = pendingRequests.stream()
                .anyMatch(request -> request.getType() == TableRequestType.REQUEST_BILL && request.getRequestedAt().isAfter(tablesSeen));
        boolean checkoutRequestBill = pendingRequests.stream()
                .anyMatch(request -> request.getType() == TableRequestType.REQUEST_BILL && request.getRequestedAt().isAfter(checkoutSeen));
        boolean checkoutReadyToClose = orderItemRepository.existsTabJustBecameReadyToClose(restaurantId, checkoutSeen);

        return NavNotificationStatusResponse.builder()
                .kitchen(kitchen)
                .tablesItemReady(tablesItemReady)
                .tablesCallWaiter(tablesCallWaiter)
                .tablesRequestBill(tablesRequestBill)
                .checkoutRequestBill(checkoutRequestBill)
                .checkoutReadyToClose(checkoutReadyToClose)
                .build();
    }

    @Transactional
    public void markSeen(UUID restaurantId, UUID userId, NavSection section) {
        NavNotificationSeen seen = navNotificationSeenRepository.findByRestaurant_IdAndUser_IdAndSection(restaurantId, userId, section)
                .orElseGet(() -> NavNotificationSeen.builder()
                        .restaurant(restaurantRepository.getReferenceById(restaurantId))
                        .user(userRepository.getReferenceById(userId))
                        .section(section)
                        .build());
        seen.setLastSeenAt(OffsetDateTime.now());
        navNotificationSeenRepository.save(seen);
    }

    private OffsetDateTime lastSeen(UUID restaurantId, UUID userId, NavSection section) {
        return navNotificationSeenRepository.findByRestaurant_IdAndUser_IdAndSection(restaurantId, userId, section)
                .map(NavNotificationSeen::getLastSeenAt)
                .orElse(NEVER_SEEN);
    }
}
