package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.WarehouseIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseIntegrationRepository extends JpaRepository<WarehouseIntegration, UUID> {
    Optional<WarehouseIntegration> findByRestaurantId(UUID restaurantId);

    // warehouse_integration_by_api_key_hash (V59) is the only way to find this row before the
    // tenant is known - Armazém Morá's sync job calls in with only its API key, no JWT. Same
    // pattern as PixChargeRepository#findByExternalChargeIdBypassingRls.
    @Query(value = "SELECT * FROM warehouse_integration_by_api_key_hash(:apiKeyHash)", nativeQuery = true)
    Optional<WarehouseIntegration> findByApiKeyHashBypassingRls(@Param("apiKeyHash") String apiKeyHash);
}
