package com.example.warehouse.controller;

import com.example.warehouse.service.MoraApiClient;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MoraProductControllerIntegrationTest {

    // Matches warehouse.sso-secret in src/test/resources/application.yml.
    private static final String SSO_SECRET = "fVAc+Q0nBv0H5okURqwD6VgUpF/sj+1QOoweA0Ph6npDHm6HkwAAxmjx4ZLF8NGdS/rCnZVtMzv/Y/Zv+z4CdQ==";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MoraApiClient moraApiClient;

    private String handoffToken(UUID restaurantId, String rawApiKey) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("purpose", "warehouse-sso");
        claims.put("restaurantId", restaurantId.toString());
        claims.put("restaurantName", "Burger House");
        claims.put("apiKey", rawApiKey);

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SSO_SECRET));
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    private String sessionTokenFor(String rawApiKey) throws Exception {
        String token = handoffToken(UUID.randomUUID(), rawApiKey);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void listProducts_returnsProductsFetchedWithThisRestaurantsApiKey() throws Exception {
        String rawApiKey = "raw-api-key-" + UUID.randomUUID();
        String session = sessionTokenFor(rawApiKey);
        UUID productId = UUID.randomUUID();

        when(moraApiClient.fetchProducts(eq(rawApiKey)))
                .thenReturn(List.of(new MoraApiClient.ProductItem(productId, "Cheeseburger")));

        mockMvc.perform(get("/api/v1/mora-products")
                        .header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$[0].productName").value("Cheeseburger"));
    }

    @Test
    void listProducts_withoutSession_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/mora-products"))
                .andExpect(status().isForbidden());
    }
}
