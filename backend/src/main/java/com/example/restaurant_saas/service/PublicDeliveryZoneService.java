package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.response.DeliveryFeeQuoteResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicDeliveryZoneService {

    private final RestaurantRepository restaurantRepository;
    private final DeliveryFeeResolver deliveryFeeResolver;
    private final TenantActivator tenantActivator;

    @Transactional(readOnly = true)
    public DeliveryFeeQuoteResponse getFeeQuote(String slug, String street, String number, String neighborhood, String city, String zipCode) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        tenantActivator.activate(restaurant.getId());
        try {
            return deliveryFeeResolver.resolve(restaurant, street, number, neighborhood, city, zipCode)
                    .map(resolved -> DeliveryFeeQuoteResponse.builder()
                            .available(true)
                            .fee(resolved.fee())
                            .distanceKm(resolved.distanceKm())
                            .method(resolved.method())
                            .build())
                    .orElseGet(() -> DeliveryFeeQuoteResponse.builder().available(false).build());
        } finally {
            tenantActivator.deactivate();
        }
    }
}
