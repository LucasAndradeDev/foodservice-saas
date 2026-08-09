package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // Bypass RLS via SECURITY DEFINER functions (see V52 migration): email is globally unique
    // and looked up before any restaurant/tenant is known - the login and forgot-password flows
    // can't set app.tenant_id ahead of time, since finding the tenant is the point of the query.
    @Query(value = "SELECT * FROM user_by_email(:email)", nativeQuery = true)
    Optional<User> findByEmailBypassingRls(@Param("email") String email);

    @Query(value = "SELECT user_email_exists(:email)", nativeQuery = true)
    boolean existsByEmailBypassingRls(@Param("email") String email);

    @Query(value = "SELECT * FROM user_by_id(:id)", nativeQuery = true)
    Optional<User> findByIdBypassingRls(@Param("id") UUID id);
    Optional<User> findByIdAndRestaurantId(UUID id, UUID restaurantId);
    List<User> findByRestaurantId(UUID restaurantId);
    long countByRestaurantIdAndRoleAndActive(UUID restaurantId, UserRole role, Boolean active);
}
