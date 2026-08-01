package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.NavNotificationSeen;
import com.example.restaurant_saas.domain.enums.NavSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NavNotificationSeenRepository extends JpaRepository<NavNotificationSeen, UUID> {
    Optional<NavNotificationSeen> findByRestaurant_IdAndUser_IdAndSection(UUID restaurantId, UUID userId, NavSection section);
}
