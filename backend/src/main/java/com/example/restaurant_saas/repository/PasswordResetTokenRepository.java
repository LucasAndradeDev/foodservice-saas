package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.PasswordResetToken;
import com.example.restaurant_saas.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findByUser(User user);
    void deleteByUser(User user);

    // Atomically claims the token: the UPDATE itself (not the earlier used==false read in
    // AuthService) is what closes the race between two concurrent requests for the same link
    // (finding #7, 2026-09-07 review) - the database serializes concurrent UPDATEs on the same row,
    // so at most one caller sees affected-rows > 0 and only that one is allowed to reset the
    // password. Must run and be checked before any other side effect of the reset.
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.id = :id AND t.used = false")
    int markUsedIfUnused(@Param("id") UUID id);
}
