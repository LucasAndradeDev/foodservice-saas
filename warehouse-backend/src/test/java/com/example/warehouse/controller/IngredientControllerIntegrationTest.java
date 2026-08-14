package com.example.warehouse.controller;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IngredientControllerIntegrationTest {

    // Matches warehouse.sso-secret in src/test/resources/application.yml.
    private static final String SSO_SECRET = "fVAc+Q0nBv0H5okURqwD6VgUpF/sj+1QOoweA0Ph6npDHm6HkwAAxmjx4ZLF8NGdS/rCnZVtMzv/Y/Zv+z4CdQ==";

    @Autowired
    private MockMvc mockMvc;

    private String handoffToken(UUID restaurantId, String restaurantName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("purpose", "warehouse-sso");
        claims.put("restaurantId", restaurantId.toString());
        claims.put("restaurantName", restaurantName);
        claims.put("apiKey", "raw-api-key-" + restaurantId);

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SSO_SECRET));
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    private String sessionTokenFor(String restaurantName) throws Exception {
        String token = handoffToken(UUID.randomUUID(), restaurantName);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void createListGetUpdate_happyPath() throws Exception {
        String session = sessionTokenFor("Burger House");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ingredients")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ground beef\",\"unit\":\"kg\",\"currentQuantity\":10,\"lowStockThreshold\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ground beef"))
                .andExpect(jsonPath("$.unit").value("kg"))
                .andExpect(jsonPath("$.currentQuantity").value(10))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.lowStock").value(false))
                .andReturn();
        String ingredientId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/ingredients").header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Ground beef"));

        mockMvc.perform(get("/api/v1/ingredients/" + ingredientId).header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ground beef"));

        mockMvc.perform(put("/api/v1/ingredients/" + ingredientId)
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentQuantity\":1.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentQuantity").value(1.5))
                .andExpect(jsonPath("$.lowStock").value(true));
    }

    @Test
    void createIngredient_duplicateName_returns400() throws Exception {
        String session = sessionTokenFor("Pizza Place");

        mockMvc.perform(post("/api/v1/ingredients")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mozzarella\",\"unit\":\"kg\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/ingredients")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"mozzarella\",\"unit\":\"kg\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingredients_areIsolatedPerRestaurantLink() throws Exception {
        String sessionA = sessionTokenFor("Restaurant A");
        String sessionB = sessionTokenFor("Restaurant B");

        MvcResult createResult = mockMvc.perform(post("/api/v1/ingredients")
                        .header("Authorization", "Bearer " + sessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tomato\",\"unit\":\"kg\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String ingredientId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/ingredients").header("Authorization", "Bearer " + sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        mockMvc.perform(get("/api/v1/ingredients/" + ingredientId).header("Authorization", "Bearer " + sessionB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listIngredients_withoutSession_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/ingredients"))
                .andExpect(status().isForbidden());
    }
}
