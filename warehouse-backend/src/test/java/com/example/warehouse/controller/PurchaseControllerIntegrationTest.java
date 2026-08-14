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
class PurchaseControllerIntegrationTest {

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

    private String createSupplier(String session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createIngredient(String session, String name, double currentQuantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ingredients")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"unit\":\"kg\",\"currentQuantity\":" + currentQuantity + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void createPurchase_addsQuantityOntoIngredientStock() throws Exception {
        String session = sessionTokenFor("Burger House");
        String supplierId = createSupplier(session, "Frigorífico Silva");
        String ingredientId = createIngredient(session, "Ground beef", 10);

        MvcResult createResult = mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + supplierId + "\",\"items\":[" +
                                "{\"ingredientId\":\"" + ingredientId + "\",\"quantity\":5,\"unitCost\":30.5}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplierId").value(supplierId))
                .andExpect(jsonPath("$.supplierName").value("Frigorífico Silva"))
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.items[0].ingredientName").value("Ground beef"))
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andReturn();
        String purchaseId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/ingredients/" + ingredientId).header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentQuantity").value(15));

        mockMvc.perform(get("/api/v1/purchases").header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(get("/api/v1/purchases/" + purchaseId).header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitCost").value(30.5));
    }

    @Test
    void createPurchase_unknownIngredient_returns400AndDoesNotCreatePurchase() throws Exception {
        String session = sessionTokenFor("Pizza Place");
        String supplierId = createSupplier(session, "Laticínios Bela Vista");

        mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + supplierId + "\",\"items\":[" +
                                "{\"ingredientId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/purchases").header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void createPurchase_supplierFromAnotherRestaurant_returns400() throws Exception {
        String sessionA = sessionTokenFor("Restaurant A");
        String sessionB = sessionTokenFor("Restaurant B");
        String supplierIdFromA = createSupplier(sessionA, "Hortifruti Central");
        String ingredientIdFromB = createIngredient(sessionB, "Tomato", 0);

        mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + sessionB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + supplierIdFromA + "\",\"items\":[" +
                                "{\"ingredientId\":\"" + ingredientIdFromB + "\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPurchases_withoutSession_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/purchases"))
                .andExpect(status().isForbidden());
    }
}
