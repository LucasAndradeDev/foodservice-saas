package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateProductRequest;
import com.example.restaurant_saas.dto.request.UpdateTableRequest;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.support.TenantTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PublicOrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

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

    private String getSlug(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
    }

    private String createCategory(String token, String name) throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName(name);
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createProduct(String token, String categoryId, String name, String price) throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName(name);
        request.setPrice(new BigDecimal(price));
        request.setCategoryId(UUID.fromString(categoryId));
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void deactivateProduct(String token, String productId) throws Exception {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setActive(false);
        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private String createTable(String token) throws Exception {
        CreateTableRequest request = new CreateTableRequest();
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void deactivateTable(String token, String tableId) throws Exception {
        UpdateTableRequest request = new UpdateTableRequest();
        request.setActive(false);
        mockMvc.perform(put("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private String selfOrderBody(String productId) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(java.util.List.of(item));
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void selfOrder_onFreeTable_shouldAutoOpenTabAndCreateOrder() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90");
        String tableId = createTable(token);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfOrderBody(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.90))
                .andExpect(jsonPath("$.total").value(51.80));

        mockMvc.perform(get("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
    }

    @Test
    void selfOrder_shouldLeaveCreatedByNull() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90");
        String tableId = createTable(token);

        MvcResult result = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfOrderBody(productId)))
                .andExpect(status().isCreated())
                .andReturn();
        String tabId = JsonPath.read(result.getResponse().getContentAsString(), "$.tabId");

        UUID restaurantId = restaurantRepository.findBySlug(slug).orElseThrow().getId();
        var orders = TenantTestSupport.withTenant(restaurantId,
                () -> orderRepository.findByTabIdAndRestaurantId(UUID.fromString(tabId), restaurantId));
        org.junit.jupiter.api.Assertions.assertNull(orders.get(0).getCreatedBy());
    }

    @Test
    void selfOrder_secondRoundOnSameTable_shouldReuseSameTab() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90");
        String tableId = createTable(token);

        MvcResult first = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfOrderBody(productId)))
                .andExpect(status().isCreated())
                .andReturn();
        String firstTabId = JsonPath.read(first.getResponse().getContentAsString(), "$.tabId");

        MvcResult second = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfOrderBody(productId)))
                .andExpect(status().isCreated())
                .andReturn();
        String secondTabId = JsonPath.read(second.getResponse().getContentAsString(), "$.tabId");

        org.junit.jupiter.api.Assertions.assertEquals(firstTabId, secondTabId);
    }

    @Test
    void selfOrder_onTableFromAnotherRestaurant_shouldReturn400() throws Exception {
        String tokenA = registerOwnerAndGetToken();
        String slugA = getSlug(tokenA);
        String categoryIdA = createCategory(tokenA, "Burgers");
        String productIdA = createProduct(tokenA, categoryIdA, "Cheeseburger", "25.90");

        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Pizza Place");
        registerRequest.setOwnerName("Owner B");
        registerRequest.setOwnerEmail("owner-b+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");
        String tokenB = registerOwnerAndGetToken();
        String tableIdB = createTable(tokenB);

        mockMvc.perform(post("/api/v1/public/menu/" + slugA + "/tables/" + tableIdB + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfOrderBody(productIdA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void selfOrder_onInactiveTable_shouldReturn403() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90");
        String tableId = createTable(token);
        deactivateTable(token, tableId);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfOrderBody(productId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void selfOrder_withInactiveProduct_shouldReturn403() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90");
        deactivateProduct(token, productId);
        String tableId = createTable(token);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfOrderBody(productId)))
                .andExpect(status().isForbidden());
    }
}
