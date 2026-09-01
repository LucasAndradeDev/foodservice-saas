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
import com.example.restaurant_saas.dto.request.UpdateDeliveryStatusRequest;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    private final DeliveryDetailsRepository deliveryDetailsRepository;
    private final OrderItemRepository orderItemRepository;
    private final CardChargeRepository cardChargeRepository;
    private final CardChargeService cardChargeService;
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
                .map(this::toResponse)
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
        return toResponse(saved);
    }

    // A courier's own restricted screen (task 28 redesign) - only their currently out-for-delivery
    // orders, the only ones they can act on (see updateStatus above).
    @Transactional(readOnly = true)
    public List<DeliveryDetailsResponse> listMyDeliveries(UUID restaurantId, UUID courierId) {
        return deliveryDetailsRepository
                .findByRestaurantIdAndCourier_IdAndStatusOrderByCreatedAtAsc(restaurantId, courierId, DeliveryStatus.OUT_FOR_DELIVERY)
                .stream()
                .map(this::toResponse)
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
            deliveryDetails.setCourier(courier);
        }

        return toResponse(deliveryDetailsRepository.save(deliveryDetails));
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
            return toResponse(deliveryDetails);
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

    private boolean isKitchenReady(UUID tabId) {
        return !orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(tabId, KITCHEN_DONE_STATUSES);
    }

    private DeliveryDetailsResponse toResponse(DeliveryDetails d) {
        // A projection, not d.getTab().getStatus() - see TabRepository#findStatusById on why the
        // entity's in-memory status can't be trusted here.
        boolean paid = tabRepository.findStatusById(d.getTab().getId()) == TabStatus.CLOSED;
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
                .courierId(d.getCourier() != null ? d.getCourier().getId() : null)
                .courierName(d.getCourier() != null ? d.getCourier().getName() : null)
                .items(toItemResponses(d.getTab().getId()))
                .billTotal(d.getTab().getBillTotal())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
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
