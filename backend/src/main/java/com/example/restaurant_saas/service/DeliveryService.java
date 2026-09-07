package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.CardCharge;
import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.CardChargeStatus;
import com.example.restaurant_saas.domain.enums.DeliveryStatus;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.AssignCourierRequest;
import com.example.restaurant_saas.dto.request.UpdateCourierLocationRequest;
import com.example.restaurant_saas.dto.request.UpdateDeliveryStatusRequest;
import com.example.restaurant_saas.dto.response.CourierLiveLocationResponse;
import com.example.restaurant_saas.dto.response.CourierOptionResponse;
import com.example.restaurant_saas.dto.response.DeliveryDetailsResponse;
import com.example.restaurant_saas.dto.response.DeliveryItemResponse;
import com.example.restaurant_saas.repository.CardChargeRepository;
import com.example.restaurant_saas.repository.DeliveryDetailsRepository;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.TabRepository;
import com.example.restaurant_saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final Map<DeliveryStatus, DeliveryStatus> NEXT_STATUS = new EnumMap<>(DeliveryStatus.class);
    static {
        NEXT_STATUS.put(DeliveryStatus.SEPARATING, DeliveryStatus.OUT_FOR_DELIVERY);
        NEXT_STATUS.put(DeliveryStatus.OUT_FOR_DELIVERY, DeliveryStatus.DELIVERED);
    }

    private static final List<ItemStatus> KITCHEN_DONE_STATUSES = List.of(ItemStatus.READY, ItemStatus.DELIVERED, ItemStatus.CANCELLED);

    // How long a courier's last reported position is trusted before treating them as offline/gone
    // dark - both for the staff "who's online" map and for whether a delivery's tracking page gets
    // a pin at all. Kept simple/hardcoded rather than a per-restaurant setting, same reasoning as
    // DeliveryPage's own delay thresholds on the frontend: a UI/UX nicety, not a business rule.
    private static final long LOCATION_STALE_AFTER_MINUTES = 5;

    private final DeliveryDetailsRepository deliveryDetailsRepository;
    private final OrderItemRepository orderItemRepository;
    private final CardChargeRepository cardChargeRepository;
    private final CardChargeService cardChargeService;
    private final DeliveryEtaService deliveryEtaService;
    private final TabRepository tabRepository;
    private final TenantActivator tenantActivator;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<DeliveryDetailsResponse> listOpenDeliveries(UUID restaurantId) {
        List<DeliveryDetails> deliveries = deliveryDetailsRepository.findByRestaurantIdAndStatusNotOrderByCreatedAtAsc(restaurantId, DeliveryStatus.DELIVERED);
        // Same reasoning as getByAccessToken's own call - this list is what staff actually watches
        // waiting for a card payment to clear, so it's just as valid a trigger as the customer's
        // own status page poll (arguably more so: staff is far more likely to have this open).
        deliveries.forEach(d -> verifyPendingCardCharge(d.getTab().getId()));
        return deliveries.stream()
                .map(d -> toResponse(d, false))
                .toList();
    }

    @Transactional
    public DeliveryDetailsResponse updateStatus(
            UUID restaurantId, UUID actingUserId, UserRole actingRole, UUID tabId, UpdateDeliveryStatusRequest request
    ) {
        DeliveryDetails deliveryDetails = deliveryDetailsRepository.findByTab_IdAndRestaurantId(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery order not found."));

        DeliveryStatus from = deliveryDetails.getStatus();
        DeliveryStatus to = request.getStatus();

        if (NEXT_STATUS.get(from) != to) {
            throw new IllegalArgumentException("Cannot change delivery status from " + from + " to " + to + ".");
        }

        // A courier's only self-service action is marking their own order delivered - dispatching
        // (SEPARATING -> OUT_FOR_DELIVERY) stays a staff action at the restaurant, same as the
        // kitchen/payment gates below. The courier-required gate on OUT_FOR_DELIVERY guarantees
        // getCourier() is non-null by the time an order can reach DELIVERED.
        if (actingRole == UserRole.COURIER) {
            boolean isOwnOrder = deliveryDetails.getCourier() != null && deliveryDetails.getCourier().getId().equals(actingUserId);
            if (to != DeliveryStatus.DELIVERED || !isOwnOrder) {
                throw new IllegalStateException("Couriers can only mark their own out-for-delivery orders as delivered.");
            }
        }

        if (to == DeliveryStatus.OUT_FOR_DELIVERY) {
            if (!isKitchenReady(tabId)) {
                throw new IllegalArgumentException("Order still being prepared in the kitchen.");
            }
            if (deliveryDetails.getTab().getStatus() != TabStatus.CLOSED) {
                throw new IllegalArgumentException("Order not fully paid yet.");
            }
            if (deliveryDetails.getCourier() == null) {
                throw new IllegalArgumentException("No courier assigned yet.");
            }
        }

        deliveryDetails.setStatus(to);
        DeliveryDetails saved = deliveryDetailsRepository.save(deliveryDetails);

        // Found testing task 29.3 end to end: without this, an order marked DELIVERED here (the
        // courier physically handed it over) left its items sitting at READY forever - there's no
        // dine-in-style "garcom entrega o prato" step afterwards for the kitchen to close out, so
        // nothing else ever advances ItemStatus. The item stayed stuck in the kitchen queue
        // indefinitely (listKitchenQueue only excludes READY/DELIVERED/CANCELLED) even though the
        // order was already done, invisible on the Delivery screen but still cluttering Cozinha.
        if (to == DeliveryStatus.DELIVERED) {
            markItemsDelivered(tabId);
        }

        return toResponse(saved, false);
    }

    private void markItemsDelivered(UUID tabId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<OrderItem> items = orderItemRepository.findByOrder_Tab_IdOrderByCreatedAtAsc(tabId).stream()
                .filter(item -> item.getStatus() != ItemStatus.DELIVERED && item.getStatus() != ItemStatus.CANCELLED)
                .peek(item -> {
                    item.setStatus(ItemStatus.DELIVERED);
                    item.setDeliveredAt(now);
                })
                .toList();
        orderItemRepository.saveAll(items);
    }

    // A courier's own restricted screen (task 28 redesign) - only their currently out-for-delivery
    // orders, the only ones they can act on (see updateStatus above).
    @Transactional(readOnly = true)
    public List<DeliveryDetailsResponse> listMyDeliveries(UUID restaurantId, UUID courierId) {
        return deliveryDetailsRepository
                .findByRestaurantIdAndCourier_IdAndStatusOrderByCreatedAtAsc(restaurantId, courierId, DeliveryStatus.OUT_FOR_DELIVERY)
                .stream()
                .map(d -> toResponse(d, false))
                .toList();
    }

    // A courier reports their own position while /my-deliveries is open, whenever logged in - not
    // gated on having an active delivery, so staff can also see who's free/nearby to hand the next
    // order to (see listLiveCouriers below).
    @Transactional
    public void updateMyLocation(UUID restaurantId, UUID courierId, UpdateCourierLocationRequest request) {
        User courier = userRepository.findByIdAndRestaurantIdAndRole(courierId, restaurantId, UserRole.COURIER)
                .orElseThrow(() -> new IllegalArgumentException("Courier not found."));
        courier.setLatitude(request.getLatitude());
        courier.setLongitude(request.getLongitude());
        courier.setLocationUpdatedAt(OffsetDateTime.now());
        userRepository.save(courier);
    }

    // Staff "who's online" map - every courier who has reported a position recently, whether or
    // not they're currently carrying a delivery. Exact coordinates: staff is a trusted,
    // authenticated context, unlike the public tracking page (see toResponse below).
    @Transactional(readOnly = true)
    public List<CourierLiveLocationResponse> listLiveCouriers(UUID restaurantId) {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(LOCATION_STALE_AFTER_MINUTES);
        Set<UUID> busyCourierIds = deliveryDetailsRepository.findCourierIdsWithActiveDelivery(restaurantId);
        return userRepository.findByRestaurantIdAndRoleAndLocationUpdatedAtAfter(restaurantId, UserRole.COURIER, threshold).stream()
                .map(u -> CourierLiveLocationResponse.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .latitude(u.getLatitude())
                        .longitude(u.getLongitude())
                        .available(!busyCourierIds.contains(u.getId()))
                        .build())
                .toList();
    }

    // Assignable-courier dropdown on the Delivery operation screen - open to every role that can
    // call assignCourier below, deliberately returning less than the full staff-management
    // UserResponse (see CourierOptionResponse).
    @Transactional(readOnly = true)
    public List<CourierOptionResponse> listAssignableCouriers(UUID restaurantId) {
        return userRepository.findByRestaurantIdAndRoleOrderByNameAsc(restaurantId, UserRole.COURIER).stream()
                .map(u -> CourierOptionResponse.builder().id(u.getId()).name(u.getName()).active(u.getActive()).build())
                .toList();
    }

    // courierId null unassigns the current courier (task 28.3) - the DeliveryPage dropdown always
    // offers a "no courier" option, same click either way.
    @Transactional
    public DeliveryDetailsResponse assignCourier(UUID restaurantId, UUID tabId, AssignCourierRequest request) {
        DeliveryDetails deliveryDetails = deliveryDetailsRepository.findByTab_IdAndRestaurantId(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery order not found."));

        // Once the order has left the restaurant, the courier carrying it is a fact about what
        // already happened, not a plan still being drafted - swapping it out mid-route would just
        // corrupt that record. SEPARATING is the only status where reassignment is still picking
        // who to hand it to.
        if (deliveryDetails.getStatus() != DeliveryStatus.SEPARATING) {
            throw new IllegalArgumentException("Courier can only be changed before the order is out for delivery.");
        }

        if (request.getCourierId() == null) {
            deliveryDetails.setCourier(null);
        } else {
            User courier = userRepository.findByIdAndRestaurantIdAndRole(request.getCourierId(), restaurantId, UserRole.COURIER)
                    .orElseThrow(() -> new IllegalArgumentException("Courier not found."));
            // listAssignableCouriers already tells the frontend who's active (CourierOptionResponse
            // filters/greys out inactive ones client-side) - enforced here too (found missing in
            // review 2026-09-07) so a direct API call can't assign a deactivated/former employee to
            // a live order regardless of what the UI shows.
            if (!Boolean.TRUE.equals(courier.getActive())) {
                throw new IllegalArgumentException("Courier is not active.");
            }
            deliveryDetails.setCourier(courier);
        }

        return toResponse(deliveryDetailsRepository.save(deliveryDetails), false);
    }

    // Looked up by the customer's own access token (task 27.3/29.1), before the tenant is known -
    // same reasoning as ReservationService#getByToken.
    @Transactional(readOnly = true)
    public DeliveryDetailsResponse getByAccessToken(String token) {
        DeliveryDetails deliveryDetails = deliveryDetailsRepository.findByAccessTokenBypassingRls(token)
                .orElseThrow(() -> new IllegalArgumentException("Delivery order not found."));
        tenantActivator.activate(deliveryDetails.getRestaurantId());
        try {
            verifyPendingCardCharge(deliveryDetails.getTab().getId());
            // refreshEtaBestEffort's write happens in its own REQUIRES_NEW transaction - this
            // outer read-only one wouldn't see it on the same in-memory entity otherwise (no
            // flush/refresh happens across transactions automatically, and re-fetching by id here
            // would just resolve back to this same persistence-context-cached instance). Applying
            // the returned value directly avoids that trap.
            refreshEtaBestEffort(deliveryDetails.getRestaurantId(), deliveryDetails.getId())
                    .ifPresent(deliveryDetails::setEtaMinutes);
            return toResponse(deliveryDetails, true);
        } finally {
            tenantActivator.deactivate();
        }
    }

    // Best-effort: a real Mercado Pago sandbox purchase's webhook is known to fail signature
    // verification (docs/CARD_PAYMENT.md, "Pegadinha do teste manual no sandbox") - production
    // payments aren't affected, but relying on the webhook alone leaves a delivery order stuck
    // showing unpaid with no staff Caixa screen polling on its behalf the way a table's checkout
    // does (CheckoutPage). Both getByAccessToken (customer's own status page) and listOpenDeliveries
    // (staff's Delivery screen) already poll on their own, so this piggybacks on whichever one
    // happens to be watched instead of adding a new client-triggered call - and never lets a
    // gateway hiccup break either read.
    private void verifyPendingCardCharge(UUID tabId) {
        try {
            List<CardCharge> pending = cardChargeRepository.findByTab_IdAndStatus(tabId, CardChargeStatus.PENDING);
            for (CardCharge charge : pending) {
                cardChargeService.verifyPendingChargeByExternalReference(charge.getExternalReference());
            }
        } catch (Exception e) {
            log.warn("Best-effort card charge verification failed for delivery tab {}", tabId, e);
        }
    }

    // Best-effort wrapper around deliveryEtaService.refreshEtaIfStale, same pattern as
    // verifyPendingCardCharge above - a routing-provider hiccup must never break the customer's
    // tracking page read. Delegates to a *different* bean rather than a same-class private method
    // on purpose: DeliveryEtaService.refreshEtaIfStale needs REQUIRES_NEW, and that only takes
    // effect on a call that actually crosses Spring's transaction proxy - a same-class
    // `this.refreshEtaIfStale(...)` call bypasses the proxy entirely and silently runs inside
    // whatever transaction the caller already has open (see DeliveryEtaService's own javadoc for
    // the incident this comment is warning about).
    private Optional<Integer> refreshEtaBestEffort(UUID restaurantId, UUID deliveryDetailsId) {
        try {
            return deliveryEtaService.refreshEtaIfStale(restaurantId, deliveryDetailsId);
        } catch (Exception e) {
            log.warn("Best-effort ETA refresh failed for delivery {}", deliveryDetailsId, e);
            return Optional.empty();
        }
    }

    private boolean isKitchenReady(UUID tabId) {
        return !orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(tabId, KITCHEN_DONE_STATUSES);
    }

    // fuzzCourierLocation is true only for getByAccessToken, the sole public/unauthenticated
    // caller - rounds the courier's position to ~100-150m instead of exposing exactly where they
    // are to anyone holding the tracking link. The other three callers (all authenticated) get
    // the exact position.
    private DeliveryDetailsResponse toResponse(DeliveryDetails d, boolean fuzzCourierLocation) {
        // A projection, not d.getTab().getStatus() - see TabRepository#findStatusById on why the
        // entity's in-memory status can't be trusted here.
        boolean paid = tabRepository.findStatusById(d.getTab().getId()) == TabStatus.CLOSED;

        // Null unless actually useful to show right now: OUT_FOR_DELIVERY, a courier assigned, and
        // that courier's last report still fresh - the frontend never has to reason about
        // staleness itself.
        Double courierLatitude = null;
        Double courierLongitude = null;
        Integer etaMinutes = null;
        User courier = d.getCourier();
        if (d.getStatus() == DeliveryStatus.OUT_FOR_DELIVERY && courier != null && courier.getLocationUpdatedAt() != null
                && courier.getLocationUpdatedAt().isAfter(OffsetDateTime.now().minusMinutes(LOCATION_STALE_AFTER_MINUTES))) {
            courierLatitude = fuzzCourierLocation ? roundCoordinate(courier.getLatitude()) : courier.getLatitude();
            courierLongitude = fuzzCourierLocation ? roundCoordinate(courier.getLongitude()) : courier.getLongitude();
            // Same gate as the position above (OUT_FOR_DELIVERY + fresh courier) - a stale cached
            // value from before the courier went quiet (or from a previous delivery entirely, once
            // this one wraps to DELIVERED) is never shown.
            etaMinutes = d.getEtaMinutes();
        }

        return DeliveryDetailsResponse.builder()
                .id(d.getId())
                .tabId(d.getTab().getId())
                .status(d.getStatus())
                .kitchenReady(isKitchenReady(d.getTab().getId()))
                .paid(paid)
                .customerName(d.getCustomerName())
                .customerPhone(d.getCustomerPhone())
                .restaurantSlug(d.getTab().getRestaurant().getSlug())
                .restaurantName(d.getTab().getRestaurant().getDisplayName())
                .restaurantPhone(d.getTab().getRestaurant().getPhone())
                .street(d.getStreet())
                .number(d.getNumber())
                .complement(d.getComplement())
                .neighborhood(d.getNeighborhood())
                .city(d.getCity())
                .zipCode(d.getZipCode())
                .referencePoint(d.getReferencePoint())
                .deliveryFee(d.getDeliveryFee())
                .deliveryDistanceKm(d.getDeliveryDistanceKm())
                .courierId(courier != null ? courier.getId() : null)
                .courierName(courier != null ? courier.getName() : null)
                .courierLatitude(courierLatitude)
                .courierLongitude(courierLongitude)
                .etaMinutes(etaMinutes)
                .items(toItemResponses(d.getTab().getId()))
                .billTotal(d.getTab().getBillTotal())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    // ~3 decimal places is ~100-150m of imprecision (less east-west the further from the equator) -
    // enough to keep the "getting close" feeling on the public tracking page without pinpointing
    // exactly where the courier is to anyone holding the link.
    private Double roundCoordinate(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 1000.0) / 1000.0;
    }

    // Top-level items only (no combo children, no modifiers/observation) - the customer's tracking
    // page needs "what did I order", not the kitchen's full breakdown.
    private List<DeliveryItemResponse> toItemResponses(UUID tabId) {
        return orderItemRepository.findByOrder_Tab_IdOrderByCreatedAtAsc(tabId).stream()
                .filter(item -> item.getParentOrderItem() == null)
                .map(this::toItemResponse)
                .toList();
    }

    private DeliveryItemResponse toItemResponse(OrderItem item) {
        return DeliveryItemResponse.builder()
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .build();
    }
}
