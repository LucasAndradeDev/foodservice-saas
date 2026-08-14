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
class RecipeControllerIntegrationTest {

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

    private String createIngredient(String session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ingredients")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"unit\":\"kg\",\"currentQuantity\":10}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void createListGetUpdate_happyPath() throws Exception {
        String session = sessionTokenFor("Burger House");
        String ingredientId = createIngredient(session, "Ground beef");
        String moraProductId = UUID.randomUUID().toString();

        MvcResult createResult = mockMvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moraProductId\":\"" + moraProductId + "\",\"moraProductName\":\"Cheeseburger\"," +
                                "\"items\":[{\"ingredientId\":\"" + ingredientId + "\",\"quantityPerUnit\":0.2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moraProductName").value("Cheeseburger"))
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.items[0].ingredientName").value("Ground beef"))
                .andExpect(jsonPath("$.items[0].quantityPerUnit").value(0.2))
                .andReturn();
        String recipeId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/recipes").header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(get("/api/v1/recipes/" + recipeId).header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moraProductId").value(moraProductId));

        mockMvc.perform(put("/api/v1/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moraProductName\":\"Cheeseburger Deluxe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moraProductName").value("Cheeseburger Deluxe"))
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void updateRecipe_withItems_replacesEntireItemList() throws Exception {
        String session = sessionTokenFor("Burger House");
        String beefId = createIngredient(session, "Ground beef");
        String cheeseId = createIngredient(session, "Cheese");
        String moraProductId = UUID.randomUUID().toString();

        MvcResult createResult = mockMvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moraProductId\":\"" + moraProductId + "\",\"moraProductName\":\"Cheeseburger\"," +
                                "\"items\":[{\"ingredientId\":\"" + beefId + "\",\"quantityPerUnit\":0.2}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String recipeId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/v1/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"ingredientId\":\"" + cheeseId + "\",\"quantityPerUnit\":0.05}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.items[0].ingredientName").value("Cheese"));
    }

    @Test
    void createRecipe_duplicateMoraProductId_returns400() throws Exception {
        String session = sessionTokenFor("Pizza Place");
        String ingredientId = createIngredient(session, "Mozzarella");
        String moraProductId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moraProductId\":\"" + moraProductId + "\",\"moraProductName\":\"Margherita\"," +
                                "\"items\":[{\"ingredientId\":\"" + ingredientId + "\",\"quantityPerUnit\":0.1}]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moraProductId\":\"" + moraProductId + "\",\"moraProductName\":\"Margherita again\"," +
                                "\"items\":[{\"ingredientId\":\"" + ingredientId + "\",\"quantityPerUnit\":0.1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRecipe_ingredientFromAnotherRestaurant_returns400() throws Exception {
        String sessionA = sessionTokenFor("Restaurant A");
        String sessionB = sessionTokenFor("Restaurant B");
        String ingredientIdFromA = createIngredient(sessionA, "Tomato");

        mockMvc.perform(post("/api/v1/recipes")
                        .header("Authorization", "Bearer " + sessionB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moraProductId\":\"" + UUID.randomUUID() + "\",\"moraProductName\":\"Salad\"," +
                                "\"items\":[{\"ingredientId\":\"" + ingredientIdFromA + "\",\"quantityPerUnit\":0.1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listRecipes_withoutSession_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/recipes"))
                .andExpect(status().isForbidden());
    }
}
