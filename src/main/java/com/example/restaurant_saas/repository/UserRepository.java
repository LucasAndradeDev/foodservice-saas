package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByIdAndRestaurantId(UUID id, UUID restaurantId);
    List<User> findByRestaurantId(UUID restaurantId);
    long countByRestaurantIdAndRoleAndActive(UUID restaurantId, UserRole role, Boolean active);
}
