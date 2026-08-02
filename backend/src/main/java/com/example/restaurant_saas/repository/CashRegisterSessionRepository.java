package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.CashRegisterSession;
import com.example.restaurant_saas.domain.enums.CashRegisterSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashRegisterSessionRepository extends JpaRepository<CashRegisterSession, UUID> {
    boolean existsByRestaurantIdAndStatus(UUID restaurantId, CashRegisterSessionStatus status);
    Optional<CashRegisterSession> findByRestaurantIdAndStatus(UUID restaurantId, CashRegisterSessionStatus status);
    List<CashRegisterSession> findTop30ByRestaurantIdOrderByOpenedAtDesc(UUID restaurantId);
}
