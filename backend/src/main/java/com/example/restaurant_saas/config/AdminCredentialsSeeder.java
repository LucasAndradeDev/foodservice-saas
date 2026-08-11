package com.example.restaurant_saas.config;

import com.example.restaurant_saas.domain.entity.AdminCredentials;
import com.example.restaurant_saas.repository.AdminCredentialsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs on every boot but only acts once: if admin_credentials has no row yet, seeds it from
 * ADMIN_USERNAME/ADMIN_PASSWORD_HASH/ADMIN_EMAIL. After that the DB row is the source of
 * truth (updatable via the admin forgot-password flow), and these env vars are only a
 * bootstrap/disaster-recovery mechanism — changing them after the row exists has no effect.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminCredentialsSeeder implements ApplicationRunner {

    private final AdminCredentialsRepository adminCredentialsRepository;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password-hash}")
    private String adminPasswordHash;

    @Value("${admin.email}")
    private String adminEmail;

    @Override
    public void run(ApplicationArguments args) {
        if (adminCredentialsRepository.count() > 0) {
            return;
        }

        if (adminUsername == null || adminUsername.isBlank() || adminPasswordHash == null || adminPasswordHash.isBlank()) {
            log.warn("No admin_credentials row and ADMIN_USERNAME/ADMIN_PASSWORD_HASH are not set; admin login will fail.");
            return;
        }

        adminCredentialsRepository.save(AdminCredentials.builder()
                .username(adminUsername)
                .passwordHash(adminPasswordHash)
                .email(adminEmail == null || adminEmail.isBlank() ? null : adminEmail)
                .build());
        log.info("Seeded admin_credentials from environment variables.");
    }
}
