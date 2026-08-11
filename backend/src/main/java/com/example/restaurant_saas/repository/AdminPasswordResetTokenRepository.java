package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.AdminPasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminPasswordResetTokenRepository extends JpaRepository<AdminPasswordResetToken, UUID> {
    Optional<AdminPasswordResetToken> findByToken(String token);
}
