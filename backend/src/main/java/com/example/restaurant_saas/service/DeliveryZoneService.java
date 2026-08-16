package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.DeliveryZone;
import com.example.restaurant_saas.dto.request.CreateDeliveryZoneRequest;
import com.example.restaurant_saas.dto.request.UpdateDeliveryZoneRequest;
import com.example.restaurant_saas.dto.response.DeliveryZoneResponse;
import com.example.restaurant_saas.repository.DeliveryZoneRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryZoneService {

    private final DeliveryZoneRepository deliveryZoneRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<DeliveryZoneResponse> listZones(UUID restaurantId) {
        return deliveryZoneRepository.findByRestaurantIdOrderByNeighborhoodAsc(restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DeliveryZoneResponse createZone(UUID restaurantId, CreateDeliveryZoneRequest request) {
        if (deliveryZoneRepository.existsByRestaurantIdAndNeighborhoodIgnoreCase(restaurantId, request.getNeighborhood())) {
            throw new IllegalArgumentException("A delivery zone with this neighborhood already exists.");
        }

        DeliveryZone zone = DeliveryZone.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .neighborhood(request.getNeighborhood())
                .fee(request.getFee())
                .build();

        return toResponse(deliveryZoneRepository.save(zone));
    }

    @Transactional
    public DeliveryZoneResponse updateZone(UUID restaurantId, UUID zoneId, UpdateDeliveryZoneRequest request) {
        DeliveryZone zone = findByIdAndRestaurant(restaurantId, zoneId);

        if (deliveryZoneRepository.existsByRestaurantIdAndNeighborhoodIgnoreCaseAndIdNot(restaurantId, request.getNeighborhood(), zoneId)) {
            throw new IllegalArgumentException("A delivery zone with this neighborhood already exists.");
        }
        zone.setNeighborhood(request.getNeighborhood());
        zone.setFee(request.getFee());
        zone.setActive(request.getActive());

        return toResponse(deliveryZoneRepository.save(zone));
    }

    @Transactional
    public void deleteZone(UUID restaurantId, UUID zoneId) {
        deliveryZoneRepository.delete(findByIdAndRestaurant(restaurantId, zoneId));
    }

    private DeliveryZone findByIdAndRestaurant(UUID restaurantId, UUID zoneId) {
        return deliveryZoneRepository.findByIdAndRestaurantId(zoneId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery zone not found."));
    }

    private DeliveryZoneResponse toResponse(DeliveryZone zone) {
        return DeliveryZoneResponse.builder()
                .id(zone.getId())
                .restaurantId(zone.getRestaurant().getId())
                .neighborhood(zone.getNeighborhood())
                .fee(zone.getFee())
                .active(zone.getActive())
                .build();
    }
}
