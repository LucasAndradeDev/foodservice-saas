package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.dto.request.UpdateRestaurantRequest;
import com.example.restaurant_saas.dto.response.RestaurantResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final GeocodingService geocodingService;

    @Transactional(readOnly = true)
    public RestaurantResponse getMyRestaurant(UUID restaurantId) {
        Restaurant restaurant = findById(restaurantId);
        return toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateMyRestaurant(UUID restaurantId, UpdateRestaurantRequest request) {
        Restaurant restaurant = findById(restaurantId);

        if (request.getTradeName() != null) {
            restaurant.setTradeName(request.getTradeName());
        }
        if (request.getSlug() != null) {
            if (!request.getSlug().isBlank() && restaurantRepository.existsBySlugAndIdNot(request.getSlug(), restaurantId)) {
                throw new IllegalArgumentException("Slug already in use.");
            }
            restaurant.setSlug(request.getSlug());
        }
        if (request.getLogo() != null) {
            restaurant.setLogo(request.getLogo());
        }
        if (request.getTableCount() != null) {
            restaurant.setTableCount(request.getTableCount());
        }
        if (request.getPhone() != null) {
            restaurant.setPhone(request.getPhone());
        }
        boolean addressChanged = false;
        if (request.getAddress() != null && !Objects.equals(request.getAddress(), restaurant.getAddress())) {
            restaurant.setAddress(request.getAddress());
            addressChanged = true;
        }
        if (request.getStreet() != null && !Objects.equals(request.getStreet(), restaurant.getStreet())) {
            restaurant.setStreet(request.getStreet());
            addressChanged = true;
        }
        if (request.getNumber() != null && !Objects.equals(request.getNumber(), restaurant.getNumber())) {
            restaurant.setNumber(request.getNumber());
            addressChanged = true;
        }
        if (request.getComplement() != null) {
            restaurant.setComplement(request.getComplement());
        }
        if (request.getNeighborhood() != null && !Objects.equals(request.getNeighborhood(), restaurant.getNeighborhood())) {
            restaurant.setNeighborhood(request.getNeighborhood());
            addressChanged = true;
        }
        if (request.getCity() != null && !Objects.equals(request.getCity(), restaurant.getCity())) {
            restaurant.setCity(request.getCity());
            addressChanged = true;
        }
        if (request.getZipCode() != null && !Objects.equals(request.getZipCode(), restaurant.getZipCode())) {
            restaurant.setZipCode(request.getZipCode());
            addressChanged = true;
        }
        if (addressChanged) {
            // Re-geocode whenever the address actually changes (task 26.5) - never left pointing
            // at the previous address's coordinates. A blank/unresolvable address, or Nominatim
            // being unavailable, just means distance-based delivery pricing stays unavailable
            // (DeliveryFeeResolver falls back to DeliveryZone) - never blocks saving settings.
            Optional<GeocodingService.GeoPoint> geocoded = geocodeRestaurantAddress(restaurant);
            restaurant.setLatitude(geocoded.map(GeocodingService.GeoPoint::latitude).orElse(null));
            restaurant.setLongitude(geocoded.map(GeocodingService.GeoPoint::longitude).orElse(null));
        }
        if (request.getDeliveryBaseFee() != null) {
            restaurant.setDeliveryBaseFee(request.getDeliveryBaseFee());
        }
        if (request.getDeliveryFeePerKm() != null) {
            restaurant.setDeliveryFeePerKm(request.getDeliveryFeePerKm());
        }
        if (request.getCnpj() != null) {
            if (!request.getCnpj().isBlank() && restaurantRepository.existsByCnpjAndIdNot(request.getCnpj(), restaurantId)) {
                throw new IllegalArgumentException("CNPJ already registered.");
            }
            restaurant.setCnpj(request.getCnpj());
        }
        if (request.getAutoPrintKitchenTickets() != null) {
            restaurant.setAutoPrintKitchenTickets(request.getAutoPrintKitchenTickets());
        }
        if (request.getKitchenWarningThresholdMinutes() != null) {
            restaurant.setKitchenWarningThresholdMinutes(request.getKitchenWarningThresholdMinutes());
        }
        if (request.getKitchenCriticalThresholdMinutes() != null) {
            restaurant.setKitchenCriticalThresholdMinutes(request.getKitchenCriticalThresholdMinutes());
        }
        if (restaurant.getKitchenCriticalThresholdMinutes() <= restaurant.getKitchenWarningThresholdMinutes()) {
            throw new IllegalArgumentException("Critical threshold must be greater than the warning threshold.");
        }
        if (request.getTableForgottenWarningThresholdMinutes() != null) {
            restaurant.setTableForgottenWarningThresholdMinutes(request.getTableForgottenWarningThresholdMinutes());
        }
        if (request.getTableForgottenCriticalThresholdMinutes() != null) {
            restaurant.setTableForgottenCriticalThresholdMinutes(request.getTableForgottenCriticalThresholdMinutes());
        }
        if (restaurant.getTableForgottenCriticalThresholdMinutes() <= restaurant.getTableForgottenWarningThresholdMinutes()) {
            throw new IllegalArgumentException("Critical threshold must be greater than the warning threshold.");
        }
        if (request.getServiceChargeEnabled() != null) {
            restaurant.setServiceChargeEnabled(request.getServiceChargeEnabled());
        }
        if (request.getServiceChargePercentage() != null) {
            restaurant.setServiceChargePercentage(request.getServiceChargePercentage());
        }
        if (request.getReservationBlockBeforeMinutes() != null) {
            restaurant.setReservationBlockBeforeMinutes(request.getReservationBlockBeforeMinutes());
        }
        if (request.getReservationBlockAfterMinutes() != null) {
            restaurant.setReservationBlockAfterMinutes(request.getReservationBlockAfterMinutes());
        }

        restaurant = restaurantRepository.save(restaurant);
        return toResponse(restaurant);
    }

    // Structured fields (street + city, task 26.5 follow-up) geocode more reliably with Nominatim
    // than free text - preferred whenever they're filled in, falling back to the free-text
    // `address` for restaurants that haven't re-entered their address through the new fields yet.
    private Optional<GeocodingService.GeoPoint> geocodeRestaurantAddress(Restaurant restaurant) {
        if (restaurant.getStreet() != null && !restaurant.getStreet().isBlank()
                && restaurant.getCity() != null && !restaurant.getCity().isBlank()) {
            return geocodingService.geocodeStructured(
                    restaurant.getStreet(), restaurant.getNumber(), restaurant.getCity(), restaurant.getZipCode());
        }
        if (restaurant.getAddress() != null && !restaurant.getAddress().isBlank()) {
            return geocodingService.geocode(restaurant.getAddress());
        }
        return Optional.empty();
    }

    private Restaurant findById(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found."));
    }

    // Package-private (not private): reused by AdminRestaurantService so the admin panel
    // doesn't need to duplicate this mapping.
    RestaurantResponse toResponse(Restaurant restaurant) {
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .tradeName(restaurant.getTradeName())
                .slug(restaurant.getSlug())
                .cnpj(restaurant.getCnpj())
                .phone(restaurant.getPhone())
                .address(restaurant.getAddress())
                .street(restaurant.getStreet())
                .number(restaurant.getNumber())
                .complement(restaurant.getComplement())
                .neighborhood(restaurant.getNeighborhood())
                .city(restaurant.getCity())
                .zipCode(restaurant.getZipCode())
                .latitude(restaurant.getLatitude())
                .longitude(restaurant.getLongitude())
                .deliveryBaseFee(restaurant.getDeliveryBaseFee())
                .deliveryFeePerKm(restaurant.getDeliveryFeePerKm())
                .logo(restaurant.getLogo())
                .tableCount(restaurant.getTableCount())
                .active(restaurant.getActive())
                .paymentDueDate(restaurant.getPaymentDueDate())
                .autoPrintKitchenTickets(restaurant.getAutoPrintKitchenTickets())
                .kitchenWarningThresholdMinutes(restaurant.getKitchenWarningThresholdMinutes())
                .kitchenCriticalThresholdMinutes(restaurant.getKitchenCriticalThresholdMinutes())
                .tableForgottenWarningThresholdMinutes(restaurant.getTableForgottenWarningThresholdMinutes())
                .tableForgottenCriticalThresholdMinutes(restaurant.getTableForgottenCriticalThresholdMinutes())
                .serviceChargeEnabled(restaurant.getServiceChargeEnabled())
                .serviceChargePercentage(restaurant.getServiceChargePercentage())
                .reservationBlockBeforeMinutes(restaurant.getReservationBlockBeforeMinutes())
                .reservationBlockAfterMinutes(restaurant.getReservationBlockAfterMinutes())
                .build();
    }
}
