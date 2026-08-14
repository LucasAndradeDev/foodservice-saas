package com.example.warehouse.repository;

import com.example.warehouse.domain.entity.RestaurantLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantLinkRepository extends JpaRepository<RestaurantLink, UUID> {
    Optional<RestaurantLink> findByMoraRestaurantId(UUID moraRestaurantId);
}
