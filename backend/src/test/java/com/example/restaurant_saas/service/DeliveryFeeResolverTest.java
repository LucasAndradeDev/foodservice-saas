package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.DeliveryZone;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.enums.DeliveryFeeMethod;
import com.example.restaurant_saas.repository.DeliveryZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryFeeResolverTest {

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private DeliveryZoneRepository deliveryZoneRepository;

    @InjectMocks
    private DeliveryFeeResolver resolver;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void resolve_withDistanceModeConfigured_andGeocodeSucceeds_shouldPriceByDistance() {
        restaurant.setLatitude(-23.5505);
        restaurant.setLongitude(-46.6333);
        restaurant.setDeliveryBaseFee(new BigDecimal("5.00"));
        restaurant.setDeliveryFeePerKm(new BigDecimal("2.00"));
        when(geocodingService.geocodeStructured(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(new GeocodingService.GeoPoint(-23.5629, -46.6544)));

        Optional<DeliveryFeeResolver.ResolvedFee> resolved = resolver.resolve(restaurant, "Rua A", "1", "Centro", "Sao Paulo", null);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().method()).isEqualTo(DeliveryFeeMethod.DISTANCE);
        assertThat(resolved.get().distanceKm()).isNotNull();
        // base 5.00 + 2.00 * ~2.55km must clear the flat 5.00 base alone.
        assertThat(resolved.get().fee()).isGreaterThan(new BigDecimal("5.00"));
        // Rounded to the nearest 50 cents - never a distance-math artifact like "R$ 15,45".
        assertThat(resolved.get().fee().remainder(new BigDecimal("0.50"))).isEqualByComparingTo(BigDecimal.ZERO);
        verifyNoInteractions(deliveryZoneRepository);
    }

    @Test
    void resolve_withDistanceModeConfigured_andGeocodeFails_shouldFallBackToZone() {
        restaurant.setLatitude(-23.5505);
        restaurant.setLongitude(-46.6333);
        restaurant.setDeliveryBaseFee(new BigDecimal("5.00"));
        restaurant.setDeliveryFeePerKm(new BigDecimal("2.00"));
        when(geocodingService.geocodeStructured(anyString(), anyString(), anyString(), any())).thenReturn(Optional.empty());
        DeliveryZone zone = DeliveryZone.builder().neighborhood("Centro").fee(new BigDecimal("8.00")).build();
        when(deliveryZoneRepository.findByRestaurantIdAndNeighborhoodIgnoreCaseAndActiveTrue(restaurant.getId(), "Centro"))
                .thenReturn(Optional.of(zone));

        Optional<DeliveryFeeResolver.ResolvedFee> resolved = resolver.resolve(restaurant, "Rua A", "1", "Centro", "Sao Paulo", null);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().method()).isEqualTo(DeliveryFeeMethod.ZONE);
        assertThat(resolved.get().distanceKm()).isNull();
        assertThat(resolved.get().fee()).isEqualByComparingTo("8.00");
    }

    @Test
    void resolve_withDistanceModeNotConfigured_shouldGoStraightToZone_withoutGeocoding() {
        DeliveryZone zone = DeliveryZone.builder().neighborhood("Centro").fee(new BigDecimal("8.00")).build();
        when(deliveryZoneRepository.findByRestaurantIdAndNeighborhoodIgnoreCaseAndActiveTrue(restaurant.getId(), "Centro"))
                .thenReturn(Optional.of(zone));

        Optional<DeliveryFeeResolver.ResolvedFee> resolved = resolver.resolve(restaurant, "Rua A", "1", "Centro", "Sao Paulo", null);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().method()).isEqualTo(DeliveryFeeMethod.ZONE);
        verifyNoInteractions(geocodingService);
    }

    @Test
    void resolve_withNeitherDistanceNorZoneAvailable_shouldBeEmpty() {
        when(deliveryZoneRepository.findByRestaurantIdAndNeighborhoodIgnoreCaseAndActiveTrue(restaurant.getId(), "Bairro Distante"))
                .thenReturn(Optional.empty());

        Optional<DeliveryFeeResolver.ResolvedFee> resolved = resolver.resolve(restaurant, "Rua A", "1", "Bairro Distante", "Sao Paulo", null);

        assertThat(resolved).isEmpty();
    }
}
