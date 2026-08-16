package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, UUID> {
    List<DeliveryZone> findByRestaurantIdOrderByNeighborhoodAsc(UUID restaurantId);
    Optional<DeliveryZone> findByIdAndRestaurantId(UUID id, UUID restaurantId);
    boolean existsByRestaurantIdAndNeighborhoodIgnoreCase(UUID restaurantId, String neighborhood);
    boolean existsByRestaurantIdAndNeighborhoodIgnoreCaseAndIdNot(UUID restaurantId, String neighborhood, UUID id);

    // Used by the public fee quote (task 26.3) and by order creation (task 26.4) - a zone
    // deactivated by the owner must stop matching immediately, same as a deactivated Product.
    Optional<DeliveryZone> findByRestaurantIdAndNeighborhoodIgnoreCaseAndActiveTrue(UUID restaurantId, String neighborhood);
}
