package com.example.warehouse.repository;

import com.example.warehouse.domain.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    List<Supplier> findByRestaurantLinkIdOrderByNameAsc(UUID restaurantLinkId);
    Optional<Supplier> findByIdAndRestaurantLinkId(UUID id, UUID restaurantLinkId);
    boolean existsByRestaurantLinkIdAndNameIgnoreCase(UUID restaurantLinkId, String name);
    boolean existsByRestaurantLinkIdAndNameIgnoreCaseAndIdNot(UUID restaurantLinkId, String name, UUID id);
}
