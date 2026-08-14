package com.example.warehouse.controller;

import com.example.warehouse.repository.RestaurantLinkRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    // Matches warehouse.sso-secret in src/test/resources/application.yml.
    private static final String SSO_SECRET = "fVAc+Q0nBv0H5okURqwD6VgUpF/sj+1QOoweA0Ph6npDHm6HkwAAxmjx4ZLF8NGdS/rCnZVtMzv/Y/Zv+z4CdQ==";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantLinkRepository restaurantLinkRepository;

    private String handoffToken(UUID restaurantId, String restaurantName, String apiKey, long expiresInMs, String purpose) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("purpose", purpose);
        claims.put("restaurantId", restaurantId.toString());
        claims.put("restaurantName", restaurantName);
        claims.put("apiKey", apiKey);

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SSO_SECRET));
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(key)
                .compact();
    }

    @Test
    void sso_withValidHandoffToken_createsRestaurantLinkAndReturnsSession() throws Exception {
        UUID moraRestaurantId = UUID.randomUUID();
        String token = handoffToken(moraRestaurantId, "Burger House", "raw-api-key-1", 60_000, "warehouse-sso");

        mockMvc.perform(post("/api/v1/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.restaurantName").value("Burger House"));

        assertThat(restaurantLinkRepository.findByMoraRestaurantId(moraRestaurantId)).isPresent();
        assertThat(restaurantLinkRepository.findByMoraRestaurantId(moraRestaurantId).get().getApiKey())
                .isEqualTo("raw-api-key-1");
    }

    @Test
    void sso_calledTwice_upsertsSameRestaurantLinkInsteadOfDuplicating() throws Exception {
        UUID moraRestaurantId = UUID.randomUUID();
        String firstToken = handoffToken(moraRestaurantId, "Pizza Place", "raw-api-key-old", 60_000, "warehouse-sso");
        mockMvc.perform(post("/api/v1/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + firstToken + "\"}"))
                .andExpect(status().isOk());
        UUID firstLinkId = restaurantLinkRepository.findByMoraRestaurantId(moraRestaurantId).orElseThrow().getId();

        String secondToken = handoffToken(moraRestaurantId, "Pizza Place", "raw-api-key-new", 60_000, "warehouse-sso");
        mockMvc.perform(post("/api/v1/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + secondToken + "\"}"))
                .andExpect(status().isOk());

        var link = restaurantLinkRepository.findByMoraRestaurantId(moraRestaurantId).orElseThrow();
        assertThat(link.getId()).isEqualTo(firstLinkId);
        assertThat(link.getApiKey()).isEqualTo("raw-api-key-new");
    }

    @Test
    void sso_withExpiredToken_returns401() throws Exception {
        String token = handoffToken(UUID.randomUUID(), "Expired Place", "raw-api-key", -1_000, "warehouse-sso");

        mockMvc.perform(post("/api/v1/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sso_withWrongPurposeClaim_returns401() throws Exception {
        String token = handoffToken(UUID.randomUUID(), "Wrong Purpose", "raw-api-key", 60_000, "something-else");

        mockMvc.perform(post("/api/v1/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sso_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-real-jwt\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sso_withBlankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
