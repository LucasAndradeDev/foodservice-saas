package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.DeliveryZone;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.response.DeliveryFeeQuoteResponse;
import com.example.restaurant_saas.repository.DeliveryZoneRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublicDeliveryZoneService {

    private final RestaurantRepository restaurantRepository;
    private final DeliveryZoneRepository deliveryZoneRepository;
    private final TenantActivator tenantActivator;

    @Transactional(readOnly = true)
    public DeliveryFeeQuoteResponse getFeeQuote(String slug, String neighborhood) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        tenantActivator.activate(restaurant.getId());
        try {
            Optional<DeliveryZone> zone = deliveryZoneRepository
                    .findByRestaurantIdAndNeighborhoodIgnoreCaseAndActiveTrue(restaurant.getId(), neighborhood);
            return zone
                    .map(z -> DeliveryFeeQuoteResponse.builder().available(true).fee(z.getFee()).build())
                    .orElseGet(() -> DeliveryFeeQuoteResponse.builder().available(false).fee(null).build());
        } finally {
            tenantActivator.deactivate();
        }
    }
}
