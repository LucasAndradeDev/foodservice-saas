package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.EmailVerificationToken;
import com.example.restaurant_saas.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findByUser(User user);
    void deleteByUser(User user);

    // Same atomic-claim race fix as PasswordResetTokenRepository#markUsedIfUnused (finding #7,
    // 2026-09-07 review) - must run and be checked before any other side effect of verification.
    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.used = true WHERE t.id = :id AND t.used = false")
    int markUsedIfUnused(@Param("id") UUID id);
}
