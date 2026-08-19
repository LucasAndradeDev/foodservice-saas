package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import com.example.restaurant_saas.domain.entity.DeliveryZone;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.enums.DeliveryStatus;
import com.example.restaurant_saas.dto.request.CreateDeliveryOrderRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.response.DeliveryOrderResponse;
import com.example.restaurant_saas.dto.response.OrderResponse;
import com.example.restaurant_saas.dto.response.TabResponse;
import com.example.restaurant_saas.repository.DeliveryDetailsRepository;
import com.example.restaurant_saas.repository.DeliveryZoneRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.TabRepository;
import com.example.restaurant_saas.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicDeliveryOrderService {

    private static final String PUBLIC_DELIVERY_ORDER_PHONE_ACTION = "public-delivery-order-phone";

    private final RestaurantRepository restaurantRepository;
    private final DeliveryDetailsRepository deliveryDetailsRepository;
    private final DeliveryZoneRepository deliveryZoneRepository;
    private final TabRepository tabRepository;
    private final TabService tabService;
    private final OrderService orderService;
    private final TenantActivator tenantActivator;
    private final RateLimitService rateLimitService;
    private final HttpServletRequest httpRequest;

    // Same config keys as PublicOrderService's WhatsApp field - same class of risk (spamming an
    // arbitrary phone number through a public endpoint), no reason for a second set of knobs.
    @Value("${security.public-order-phone-rate-limit.max-attempts}")
    private int phoneMaxAttempts;

    @Value("${security.public-order-phone-rate-limit.window-minutes}")
    private long phoneWindowMinutes;

    @Value("${security.public-order-phone-rate-limit.block-minutes}")
    private long phoneBlockMinutes;

    @Transactional
    public DeliveryOrderResponse createDeliveryOrder(String slug, CreateDeliveryOrderRequest request) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        String normalizedPhone = request.getCustomerPhone().replaceAll("\\D", "");
        rateLimitService.checkAllowed(PUBLIC_DELIVERY_ORDER_PHONE_ACTION, httpRequest, normalizedPhone);
        rateLimitService.recordAttempt(
                PUBLIC_DELIVERY_ORDER_PHONE_ACTION, httpRequest, normalizedPhone,
                phoneMaxAttempts, phoneWindowMinutes, phoneBlockMinutes
        );

        tenantActivator.activate(restaurant.getId());
        try {
            // Looked up (and rejected if unserved) before creating anything - never trust a fee
            // the client might have shown from the preview quote (task 26.3), and never leave a
            // tab/order behind for a neighborhood the restaurant doesn't actually deliver to.
            DeliveryZone zone = deliveryZoneRepository
                    .findByRestaurantIdAndNeighborhoodIgnoreCaseAndActiveTrue(restaurant.getId(), request.getNeighborhood())
                    .orElseThrow(() -> new IllegalArgumentException("We don't deliver to this neighborhood yet."));

            // No tables, same shape as a Balcao tab - what marks this one as a delivery order is
            // the DeliveryDetails row created below, not anything on the Tab itself.
            OpenTabRequest openTabRequest = new OpenTabRequest();
            openTabRequest.setTableIds(List.of());
            TabResponse tabResponse = tabService.openTab(restaurant.getId(), openTabRequest);

            CreateOrderRequest orderRequest = new CreateOrderRequest();
            orderRequest.setItems(request.getItems());
            OrderResponse order = orderService.createOrder(restaurant.getId(), tabResponse.getId(), orderRequest, null);

            DeliveryDetails deliveryDetails = DeliveryDetails.builder()
                    .restaurantId(restaurant.getId())
                    .tab(tabRepository.getReferenceById(tabResponse.getId()))
                    .customerName(request.getCustomerName())
                    .customerPhone(request.getCustomerPhone())
                    .street(request.getStreet())
                    .number(request.getNumber())
                    .complement(request.getComplement())
                    .neighborhood(request.getNeighborhood())
                    .city(request.getCity())
                    .zipCode(request.getZipCode())
                    .referencePoint(request.getReferencePoint())
                    .deliveryFee(zone.getFee())
                    .accessToken(UUID.randomUUID().toString())
                    .status(DeliveryStatus.SEPARATING)
                    .build();
            deliveryDetailsRepository.save(deliveryDetails);

            return DeliveryOrderResponse.builder()
                    .tabId(tabResponse.getId())
                    .accessToken(deliveryDetails.getAccessToken())
                    .deliveryFee(deliveryDetails.getDeliveryFee())
                    .order(order)
                    .build();
        } finally {
            tenantActivator.deactivate();
        }
    }
}
