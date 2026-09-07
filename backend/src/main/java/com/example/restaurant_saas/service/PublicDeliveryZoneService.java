package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.response.DeliveryFeeQuoteResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicDeliveryZoneService {

    // No caller-supplied identifier exists for this endpoint (no phone/email/token like the other
    // public actions), so every request from a given IP shares one bucket - RateLimitService's
    // per-identifier and IP-only checks collapse into the same effective limit here, which is the
    // intended, stricter behavior for an action a legitimate customer only needs a handful of times
    // while filling in one address.
    private static final String FEE_QUOTE_ACTION = "public-delivery-fee-quote";
    private static final String FEE_QUOTE_IDENTIFIER = "quote";

    private static final int MAX_STREET_LENGTH = 255;
    private static final int MAX_NUMBER_LENGTH = 20;
    private static final int MAX_NEIGHBORHOOD_LENGTH = 100;
    private static final int MAX_CITY_LENGTH = 100;
    private static final int MAX_ZIP_CODE_LENGTH = 10;

    private final RestaurantRepository restaurantRepository;
    private final DeliveryFeeResolver deliveryFeeResolver;
    private final TenantActivator tenantActivator;
    private final RateLimitService rateLimitService;
    private final HttpServletRequest httpRequest;

    @Value("${security.public-delivery-fee-quote-rate-limit.max-attempts}")
    private int quoteMaxAttempts;

    @Value("${security.public-delivery-fee-quote-rate-limit.window-minutes}")
    private long quoteWindowMinutes;

    @Value("${security.public-delivery-fee-quote-rate-limit.block-minutes}")
    private long quoteBlockMinutes;

    @Transactional(readOnly = true)
    public DeliveryFeeQuoteResponse getFeeQuote(String slug, String street, String number, String neighborhood, String city, String zipCode) {
        rateLimitService.checkAllowed(FEE_QUOTE_ACTION, httpRequest, FEE_QUOTE_IDENTIFIER);
        rateLimitService.recordAttempt(
                FEE_QUOTE_ACTION, httpRequest, FEE_QUOTE_IDENTIFIER,
                quoteMaxAttempts, quoteWindowMinutes, quoteBlockMinutes
        );

        validateLength("Street", street, MAX_STREET_LENGTH);
        validateLength("Number", number, MAX_NUMBER_LENGTH);
        validateLength("Neighborhood", neighborhood, MAX_NEIGHBORHOOD_LENGTH);
        validateLength("City", city, MAX_CITY_LENGTH);
        validateLength("Zip code", zipCode, MAX_ZIP_CODE_LENGTH);

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

    private void validateLength(String fieldName, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
    }
}
