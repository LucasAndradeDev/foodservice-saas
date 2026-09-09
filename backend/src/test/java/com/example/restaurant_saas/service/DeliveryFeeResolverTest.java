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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryFeeResolverTest {

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private RouteDistanceService routeDistanceService;

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
    void resolve_withDistanceModeConfigured_andGeocodeSucceeds_butNoRouteProviderAvailable_shouldFallBackToHaversine() {
        restaurant.setLatitude(-23.5505);
        restaurant.setLongitude(-46.6333);
        restaurant.setDeliveryBaseFee(new BigDecimal("5.00"));
        restaurant.setDeliveryFeePerKm(new BigDecimal("2.00"));
        when(geocodingService.geocodeStructured(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(new GeocodingService.GeoPoint(-23.5629, -46.6544)));
        // Unstubbed routeDistanceService already defaults to Optional.empty() (Mockito's built-in
        // behavior for Optional-returning methods) - spelled out here so the fallback this test
        // actually exercises is obvious at a glance, not an accident of Mockito defaults.
        when(routeDistanceService.route(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(Optional.empty());

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
    void resolve_withRouteDistanceAvailable_shouldUseItInsteadOfHaversine() {
        restaurant.setLatitude(-23.5505);
        restaurant.setLongitude(-46.6333);
        restaurant.setDeliveryBaseFee(new BigDecimal("5.00"));
        restaurant.setDeliveryFeePerKm(new BigDecimal("2.00"));
        when(geocodingService.geocodeStructured(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(new GeocodingService.GeoPoint(-23.5629, -46.6544)));
        // Deliberately far from the ~2.55km Haversine distance between these two points, so the
        // assertion below can only pass if the route distance actually won out.
        when(routeDistanceService.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(new RouteDistanceService.RouteResult(10.0, 18.0)));

        Optional<DeliveryFeeResolver.ResolvedFee> resolved = resolver.resolve(restaurant, "Rua A", "1", "Centro", "Sao Paulo", null);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().method()).isEqualTo(DeliveryFeeMethod.DISTANCE);
        assertThat(resolved.get().distanceKm()).isEqualByComparingTo("10.00");
        // base 5.00 + 2.00 * 10.00 = 25.00, already a multiple of 0.50 - no rounding drift to
        // account for in this assertion.
        assertThat(resolved.get().fee()).isEqualByComparingTo("25.00");
        // Geocoded point carried through - DeliveryService's live ETA reuses it instead of
        // re-geocoding the customer's address on every refresh.
        assertThat(resolved.get().customerLatitude()).isEqualTo(-23.5629);
        assertThat(resolved.get().customerLongitude()).isEqualTo(-46.6544);
    }

    @Test
    void resolve_withStructuredGeocodeFailing_shouldFallBackToNeighborhoodCentroid() {
        restaurant.setLatitude(-23.5505);
        restaurant.setLongitude(-46.6333);
        restaurant.setDeliveryBaseFee(new BigDecimal("5.00"));
        restaurant.setDeliveryFeePerKm(new BigDecimal("2.00"));
        // Street-level lookup fails (e.g. a small street missing from OpenStreetMap, found
        // 2026-09-09 with "Vila Valença" in Moura Brasil, Fortaleza) but the neighborhood alone
        // still resolves - distance pricing should use that coarser point instead of dropping
        // straight to the DeliveryZone table.
        when(geocodingService.geocodeStructured(anyString(), anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(geocodingService.geocode("Centro, Sao Paulo"))
                .thenReturn(Optional.of(new GeocodingService.GeoPoint(-23.5629, -46.6544)));
        when(routeDistanceService.route(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(Optional.empty());

        Optional<DeliveryFeeResolver.ResolvedFee> resolved = resolver.resolve(restaurant, "Rua Sem Nome no OSM", "1", "Centro", "Sao Paulo", null);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().method()).isEqualTo(DeliveryFeeMethod.DISTANCE);
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
        assertThat(resolved.get().customerLatitude()).isNull();
        assertThat(resolved.get().customerLongitude()).isNull();
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
    void resolve_withDistanceWithinMaxDeliveryDistance_shouldPriceByDistance() {
        restaurant.setLatitude(-23.5505);
        restaurant.setLongitude(-46.6333);
        restaurant.setDeliveryBaseFee(new BigDecimal("5.00"));
        restaurant.setDeliveryFeePerKm(new BigDecimal("2.00"));
        restaurant.setMaxDeliveryDistanceKm(new BigDecimal("15.00"));
        when(geocodingService.geocodeStructured(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(new GeocodingService.GeoPoint(-23.5629, -46.6544)));
        when(routeDistanceService.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(new RouteDistanceService.RouteResult(10.0, 18.0)));

        Optional<DeliveryFeeResolver.ResolvedFee> resolved = resolver.resolve(restaurant, "Rua A", "1", "Centro", "Sao Paulo", null);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().method()).isEqualTo(DeliveryFeeMethod.DISTANCE);
    }

    // finding #3, 2026-09-07 review: without this cap, any address that geocodes successfully was
    // priced and accepted no matter how far - even a different city.
    @Test
    void resolve_withDistanceBeyondMaxDeliveryDistance_shouldFallBackToZone() {
        restaurant.setLatitude(-23.5505);
        restaurant.setLongitude(-46.6333);
        restaurant.setDeliveryBaseFee(new BigDecimal("5.00"));
        restaurant.setDeliveryFeePerKm(new BigDecimal("2.00"));
        restaurant.setMaxDeliveryDistanceKm(new BigDecimal("15.00"));
        when(geocodingService.geocodeStructured(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(new GeocodingService.GeoPoint(-23.5629, -46.6544)));
        // Beyond the 15km cap - route distance wins over Haversine as usual, but now rejects.
        when(routeDistanceService.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(new RouteDistanceService.RouteResult(20.0, 30.0)));
        DeliveryZone zone = DeliveryZone.builder().neighborhood("Centro").fee(new BigDecimal("8.00")).build();
        when(deliveryZoneRepository.findByRestaurantIdAndNeighborhoodIgnoreCaseAndActiveTrue(restaurant.getId(), "Centro"))
                .thenReturn(Optional.of(zone));

        Optional<DeliveryFeeResolver.ResolvedFee> resolved = resolver.resolve(restaurant, "Rua A", "1", "Centro", "Sao Paulo", null);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().method()).isEqualTo(DeliveryFeeMethod.ZONE);
        assertThat(resolved.get().fee()).isEqualByComparingTo("8.00");
    }

    @Test
    void resolve_withNeitherDistanceNorZoneAvailable_shouldBeEmpty() {
        when(deliveryZoneRepository.findByRestaurantIdAndNeighborhoodIgnoreCaseAndActiveTrue(restaurant.getId(), "Bairro Distante"))
                .thenReturn(Optional.empty());

        Optional<DeliveryFeeResolver.ResolvedFee> resolved = resolver.resolve(restaurant, "Rua A", "1", "Bairro Distante", "Sao Paulo", null);

        assertThat(resolved).isEmpty();
    }
}
