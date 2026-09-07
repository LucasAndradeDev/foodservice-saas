package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.DeliveryZone;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.enums.DeliveryFeeMethod;
import com.example.restaurant_saas.repository.DeliveryZoneRepository;
import com.example.restaurant_saas.util.HaversineUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

// Single place deciding how a delivery order gets priced - used by both the public fee preview
// (PublicDeliveryZoneService) and order creation (PublicDeliveryOrderService), so a quote and the
// fee actually charged can never disagree on method. Distance is the priority method (task 26.5,
// docs/DELIVERY.md "v2: geo real"); DeliveryZone (task 26, neighborhood-based) is the fallback
// used whenever the restaurant hasn't configured distance pricing, or the customer's address
// doesn't geocode.
@Service
@RequiredArgsConstructor
public class DeliveryFeeResolver {

    private final GeocodingService geocodingService;
    private final RouteDistanceService routeDistanceService;
    private final DeliveryZoneRepository deliveryZoneRepository;

    // customerLatitude/Longitude are null for a ZONE fee (no geocoding happens) - carried through
    // so PublicDeliveryOrderService can cache them on DeliveryDetails, letting DeliveryService's
    // live ETA (once the order is OUT_FOR_DELIVERY) route from the courier's position without
    // re-geocoding the address on every refresh.
    public record ResolvedFee(
            BigDecimal fee, DeliveryFeeMethod method, BigDecimal distanceKm,
            Double customerLatitude, Double customerLongitude
    ) {
    }

    // Rounded to the nearest 50 cents so the customer sees "R$ 15,50" instead of a distance-math
    // artifact like "R$ 15,45" - only applies to the distance-based fee (a zone's fee is already
    // whatever exact value the owner typed in, nothing to round there).
    private static final BigDecimal ROUNDING_STEP = new BigDecimal("0.50");

    public Optional<ResolvedFee> resolve(Restaurant restaurant, String street, String number, String neighborhood, String city, String zipCode) {
        Optional<ResolvedFee> byDistance = resolveByDistance(restaurant, street, number, city, zipCode);
        if (byDistance.isPresent()) {
            return byDistance;
        }
        return deliveryZoneRepository
                .findByRestaurantIdAndNeighborhoodIgnoreCaseAndActiveTrue(restaurant.getId(), neighborhood)
                .map(this::toZoneFee);
    }

    private Optional<ResolvedFee> resolveByDistance(Restaurant restaurant, String street, String number, String city, String zipCode) {
        if (restaurant.getLatitude() == null || restaurant.getLongitude() == null
                || restaurant.getDeliveryBaseFee() == null || restaurant.getDeliveryFeePerKm() == null) {
            return Optional.empty();
        }

        return geocodingService.geocodeStructured(street, number, city, zipCode)
                .flatMap(point -> {
                    // Real road distance (docs/DELIVERY.md "v3: rota real") whenever a routing provider
                    // is available; straight-line Haversine is only the fallback for when all three of
                    // them are unavailable (see RouteDistanceService) - never blocks pricing either way.
                    double distance = routeDistanceService
                            .route(restaurant.getLatitude(), restaurant.getLongitude(), point.latitude(), point.longitude())
                            .map(RouteDistanceService.RouteResult::distanceKm)
                            .orElseGet(() -> HaversineUtil.distanceKm(
                                    restaurant.getLatitude(), restaurant.getLongitude(), point.latitude(), point.longitude()));

                    BigDecimal distanceKm = BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);
                    // Farther than the restaurant's configured radius (finding #3, 2026-09-07 review):
                    // fall back to DeliveryZone instead of pricing/accepting an out-of-range address.
                    if (restaurant.getMaxDeliveryDistanceKm() != null
                            && distanceKm.compareTo(restaurant.getMaxDeliveryDistanceKm()) > 0) {
                        return Optional.empty();
                    }

                    BigDecimal rawFee = restaurant.getDeliveryBaseFee()
                            .add(restaurant.getDeliveryFeePerKm().multiply(distanceKm));
                    BigDecimal fee = rawFee.divide(ROUNDING_STEP, 0, RoundingMode.HALF_UP)
                            .multiply(ROUNDING_STEP)
                            .setScale(2, RoundingMode.HALF_UP);
                    return Optional.of(new ResolvedFee(fee, DeliveryFeeMethod.DISTANCE, distanceKm, point.latitude(), point.longitude()));
                });
    }

    private ResolvedFee toZoneFee(DeliveryZone zone) {
        return new ResolvedFee(zone.getFee(), DeliveryFeeMethod.ZONE, null, null, null);
    }
}
