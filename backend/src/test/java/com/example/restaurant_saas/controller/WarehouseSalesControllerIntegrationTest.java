package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.domain.enums.ItemStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WarehouseSalesControllerIntegrationTest {

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

    private String createTableAndGetId(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTableRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String openTabAndGetId(String token, String tableId) throws Exception {
        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of(UUID.fromString(tableId)));
        MvcResult result = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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

    private String createProductAndGetId(String token, String categoryId, String name) throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName(name);
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(UUID.fromString(categoryId));
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createOrderAndGetFirstItemId(String token, String tabId, String productId) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(2);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        MvcResult result = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");
    }

    private void moveItemToStatus(String token, String itemId, String status) throws Exception {
        UpdateOrderItemStatusRequest request = new UpdateOrderItemStatusRequest();
        request.setStatus(ItemStatus.valueOf(status));
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
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

    private record TestSetup(String ownerToken, String itemId) {}

    private TestSetup setupDeliveredItem() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);

        moveItemToStatus(ownerToken, itemId, "PREPARING");
        moveItemToStatus(ownerToken, itemId, "READY");
        moveItemToStatus(ownerToken, itemId, "DELIVERED");

        return new TestSetup(ownerToken, itemId);
    }

    @Test
    void listSales_withValidApiKeyAndSince_returnsDeliveredItem() throws Exception {
        OffsetDateTime before = OffsetDateTime.now().minusMinutes(1);
        TestSetup setup = setupDeliveredItem();
        String rawApiKey = mintRawApiKey(setup.ownerToken());

        mockMvc.perform(get("/api/v1/internal/warehouse/sales")
                        .header(API_KEY_HEADER, rawApiKey)
                        .param("since", before.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Cheeseburger"))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[0].deliveredAt").isNotEmpty());
    }

    @Test
    void listSales_sinceAfterDelivery_returnsEmpty() throws Exception {
        TestSetup setup = setupDeliveredItem();
        String rawApiKey = mintRawApiKey(setup.ownerToken());
        OffsetDateTime after = OffsetDateTime.now().plusMinutes(1);

        mockMvc.perform(get("/api/v1/internal/warehouse/sales")
                        .header(API_KEY_HEADER, rawApiKey)
                        .param("since", after.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listSales_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/internal/warehouse/sales")
                        .param("since", OffsetDateTime.now().minusHours(1).toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listSales_withInvalidApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/internal/warehouse/sales")
                        .header(API_KEY_HEADER, "not-a-real-key")
                        .param("since", OffsetDateTime.now().minusHours(1).toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listSales_apiKeyOnlySeesItsOwnRestaurant() throws Exception {
        OffsetDateTime before = OffsetDateTime.now().minusMinutes(1);
        TestSetup setupA = setupDeliveredItem();
        mintRawApiKey(setupA.ownerToken());

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        registerRequest = otherRestaurant;
        String otherOwnerToken = registerOwnerAndGetToken();
        String rawApiKeyB = mintRawApiKey(otherOwnerToken);

        mockMvc.perform(get("/api/v1/internal/warehouse/sales")
                        .header(API_KEY_HEADER, rawApiKeyB)
                        .param("since", before.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
