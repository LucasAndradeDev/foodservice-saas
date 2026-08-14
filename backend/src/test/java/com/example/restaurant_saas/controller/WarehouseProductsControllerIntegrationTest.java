package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.ProductType;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WarehouseProductsControllerIntegrationTest {

    private static final String API_KEY_HEADER = "X-Warehouse-Api-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${warehouse.sso-secret}")
    private String ssoSecret;

    private RegisterRestaurantRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Burger House");
        registerRequest.setOwnerName("Owner");
        registerRequest.setOwnerEmail("owner+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");
    }

    private String registerOwnerAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createCategoryAndGetId(String token) throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Burgers " + System.nanoTime());
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void createProduct(String token, String categoryId, String name, ProductType type) throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName(name);
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));
        request.setType(type);
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /** Mints a handoff via the real endpoint and decodes the raw API key out of its token claim. */
    private String mintRawApiKey(String ownerToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/warehouse/handoff")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String handoffUrl = JsonPath.read(result.getResponse().getContentAsString(), "$.handoffUrl");
        String token = handoffUrl.substring(handoffUrl.indexOf("token=") + "token=".length());

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(ssoSecret));
        Jws<io.jsonwebtoken.Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        return jws.getPayload().get("apiKey", String.class);
    }

    @Test
    void listProducts_returnsActiveSimpleProductAndExcludesCombo() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        createProduct(ownerToken, categoryId, "Cheeseburger", ProductType.SIMPLE);
        createProduct(ownerToken, categoryId, "Combo Família", ProductType.COMBO);
        String rawApiKey = mintRawApiKey(ownerToken);

        mockMvc.perform(get("/api/v1/internal/warehouse/products")
                        .header(API_KEY_HEADER, rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Cheeseburger"))
                .andExpect(jsonPath("$[0].productId").isNotEmpty());
    }

    @Test
    void listProducts_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/internal/warehouse/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listProducts_withInvalidApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/internal/warehouse/products")
                        .header(API_KEY_HEADER, "not-a-real-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listProducts_apiKeyOnlySeesItsOwnRestaurant() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        createProduct(ownerToken, categoryId, "Cheeseburger", ProductType.SIMPLE);
        mintRawApiKey(ownerToken);

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        registerRequest = otherRestaurant;
        String otherOwnerToken = registerOwnerAndGetToken();
        String rawApiKeyB = mintRawApiKey(otherOwnerToken);

        mockMvc.perform(get("/api/v1/internal/warehouse/products")
                        .header(API_KEY_HEADER, rawApiKeyB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
