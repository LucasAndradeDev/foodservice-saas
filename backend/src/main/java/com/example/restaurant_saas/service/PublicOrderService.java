package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.response.OrderResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicOrderService {

    private static final String PUBLIC_ORDER_PHONE_ACTION = "public-order-phone";

    private final RestaurantRepository restaurantRepository;
    private final TabService tabService;
    private final OrderService orderService;
    private final TenantActivator tenantActivator;
    private final RateLimitService rateLimitService;
    private final HttpServletRequest httpRequest;

    @Value("${security.public-order-phone-rate-limit.max-attempts}")
    private int phoneMaxAttempts;

    @Value("${security.public-order-phone-rate-limit.window-minutes}")
    private long phoneWindowMinutes;

    @Value("${security.public-order-phone-rate-limit.block-minutes}")
    private long phoneBlockMinutes;

    @Transactional
    public OrderResponse createOrder(String slug, UUID tableId, CreateOrderRequest request) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        String customerPhone = request.getCustomerPhone();
        if (customerPhone != null && !customerPhone.isBlank()) {
            String normalizedPhone = customerPhone.replaceAll("\\D", "");
            rateLimitService.checkAllowed(PUBLIC_ORDER_PHONE_ACTION, httpRequest, normalizedPhone);
            rateLimitService.recordAttempt(
                    PUBLIC_ORDER_PHONE_ACTION, httpRequest, normalizedPhone,
                    phoneMaxAttempts, phoneWindowMinutes, phoneBlockMinutes
            );
        }

        tenantActivator.activate(restaurant.getId());
        try {
            Tab tab = tabService.openOrGetTabForTable(restaurant.getId(), tableId, customerPhone);
            return orderService.createOrder(restaurant.getId(), tab.getId(), request, null);
        } finally {
            tenantActivator.deactivate();
        }
    }
}
