package com.example.restaurant_saas.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupServiceTest {

    @Test
    void buildConnectionUri_withSslMode_shouldInsertUsernameAfterScheme() {
        String uri = BackupService.buildConnectionUri(
                "jdbc:postgresql://db.example.com:5432/mora?sslmode=require",
                "app_user"
        );

        assertThat(uri).isEqualTo("postgresql://app_user@db.example.com:5432/mora?sslmode=require");
    }

    @Test
    void buildConnectionUri_withoutQueryParams_shouldStillWork() {
        String uri = BackupService.buildConnectionUri(
                "jdbc:postgresql://localhost:5432/restaurant_saas",
                "postgres"
        );

        assertThat(uri).isEqualTo("postgresql://postgres@localhost:5432/restaurant_saas");
    }

    @Test
    void buildConnectionUri_withSpecialCharactersInUsername_shouldUrlEncodeThem() {
        String uri = BackupService.buildConnectionUri(
                "jdbc:postgresql://localhost:5432/restaurant_saas",
                "user@name"
        );

        assertThat(uri).isEqualTo("postgresql://user%40name@localhost:5432/restaurant_saas");
    }

    @Test
    void buildConnectionUri_withInvalidFormat_shouldThrow() {
        assertThatThrownBy(() -> BackupService.buildConnectionUri("not-a-jdbc-url", "postgres"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
