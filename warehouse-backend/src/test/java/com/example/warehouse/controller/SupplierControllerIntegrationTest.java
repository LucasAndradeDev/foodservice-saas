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
class SupplierControllerIntegrationTest {

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

        MvcResult createResult = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Frigorífico Silva\",\"contact\":\"(11) 99999-0000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Frigorífico Silva"))
                .andExpect(jsonPath("$.contact").value("(11) 99999-0000"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        String supplierId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/suppliers").header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Frigorífico Silva"));

        mockMvc.perform(get("/api/v1/suppliers/" + supplierId).header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Frigorífico Silva"));

        mockMvc.perform(put("/api/v1/suppliers/" + supplierId)
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void createSupplier_duplicateName_returns400() throws Exception {
        String session = sessionTokenFor("Pizza Place");

        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Laticínios Bela Vista\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"laticínios bela vista\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suppliers_areIsolatedPerRestaurantLink() throws Exception {
        String sessionA = sessionTokenFor("Restaurant A");
        String sessionB = sessionTokenFor("Restaurant B");

        MvcResult createResult = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + sessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hortifruti Central\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String supplierId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/suppliers").header("Authorization", "Bearer " + sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        mockMvc.perform(get("/api/v1/suppliers/" + supplierId).header("Authorization", "Bearer " + sessionB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSuppliers_withoutSession_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers"))
                .andExpect(status().isForbidden());
    }
}
