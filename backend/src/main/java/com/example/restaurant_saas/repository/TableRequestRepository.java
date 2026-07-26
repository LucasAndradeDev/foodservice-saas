package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.TableRequest;
import com.example.restaurant_saas.domain.enums.TableRequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TableRequestRepository extends JpaRepository<TableRequest, UUID> {
    List<TableRequest> findByRestaurantIdAndAcknowledgedAtIsNull(UUID restaurantId);
    Optional<TableRequest> findByIdAndRestaurantId(UUID id, UUID restaurantId);
    Optional<TableRequest> findByTableIdAndTypeAndAcknowledgedAtIsNull(UUID tableId, TableRequestType type);
}
