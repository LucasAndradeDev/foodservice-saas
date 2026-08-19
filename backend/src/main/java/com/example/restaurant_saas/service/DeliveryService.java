package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.CardCharge;
import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.enums.CardChargeStatus;
import com.example.restaurant_saas.domain.enums.DeliveryStatus;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.dto.request.UpdateDeliveryStatusRequest;
import com.example.restaurant_saas.dto.response.DeliveryDetailsResponse;
import com.example.restaurant_saas.dto.response.DeliveryItemResponse;
import com.example.restaurant_saas.repository.CardChargeRepository;
import com.example.restaurant_saas.repository.DeliveryDetailsRepository;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.TabRepository;
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
    public DeliveryDetailsResponse updateStatus(UUID restaurantId, UUID tabId, UpdateDeliveryStatusRequest request) {
        DeliveryDetails deliveryDetails = deliveryDetailsRepository.findByTab_IdAndRestaurantId(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery order not found."));

        DeliveryStatus from = deliveryDetails.getStatus();
        DeliveryStatus to = request.getStatus();

        if (NEXT_STATUS.get(from) != to) {
            throw new IllegalArgumentException("Cannot change delivery status from " + from + " to " + to + ".");
        }
        if (to == DeliveryStatus.OUT_FOR_DELIVERY) {
            if (!isKitchenReady(tabId)) {
                throw new IllegalArgumentException("Order still being prepared in the kitchen.");
            }
            if (deliveryDetails.getTab().getStatus() != TabStatus.CLOSED) {
                throw new IllegalArgumentException("Order not fully paid yet.");
            }
        }

        deliveryDetails.setStatus(to);
        DeliveryDetails saved = deliveryDetailsRepository.save(deliveryDetails);
        return toResponse(saved);
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
                .restaurantName(d.getTab().getRestaurant().getTradeName() != null
                        ? d.getTab().getRestaurant().getTradeName()
                        : d.getTab().getRestaurant().getName())
                .restaurantPhone(d.getTab().getRestaurant().getPhone())
                .street(d.getStreet())
                .number(d.getNumber())
                .complement(d.getComplement())
                .neighborhood(d.getNeighborhood())
                .city(d.getCity())
                .zipCode(d.getZipCode())
                .referencePoint(d.getReferencePoint())
                .deliveryFee(d.getDeliveryFee())
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
