package com.example.restaurant_saas.security;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "fVAc+Q0nBv0H5okURqwD6VgUpF/sj+1QOoweA0Ph6npDHm6HkwAAxmjx4ZLF8NGdS/rCnZVtMzv/Y/Zv+z4CdQ==";

    private JwtService jwtService;
    private UserDetailsImpl userDetails;
    private UUID restaurantId;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);

        restaurantId = UUID.randomUUID();
        Restaurant restaurant = Restaurant.builder().id(restaurantId).name("Test Restaurant").build();
        User user = User.builder()
                .id(UUID.randomUUID())
                .restaurant(restaurant)
                .name("Owner")
                .email("owner@test.com")
                .password("hash")
                .role(UserRole.OWNER)
                .active(true)
                .build();
        userDetails = new UserDetailsImpl(user);
    }

    @Test
    void generateToken_shouldEmbedEmailAndRestaurantId() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.extractEmail(token)).isEqualTo("owner@test.com");
        assertThat(jwtService.extractRestaurantId(token)).isEqualTo(restaurantId);
    }

    @Test
    void isTokenValid_shouldReturnTrueForMatchingUserAndUnexpiredToken() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, "owner@test.com")).isTrue();
    }

    @Test
    void isTokenValid_shouldReturnFalseForDifferentUser() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, "another@test.com")).isFalse();
    }

    @Test
    void isTokenValid_shouldReturnFalseForExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1_000L);
        String expiredToken = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(expiredToken, "owner@test.com")).isFalse();
    }
}
