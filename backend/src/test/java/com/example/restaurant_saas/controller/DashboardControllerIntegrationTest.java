package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.repository.RestaurantRepository;

import com.example.restaurant_saas.dto.request.LoginRequest;

import com.example.restaurant_saas.domain.entity.Restaurant;

import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PaymentEntryRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    // A new signup is unapproved by default (AuthService#registerRestaurant) and can't log in -
    // approve it directly (Restaurant carries no tenant RLS/@Filter, see AdminRestaurantService)
    // then log in to get a working token, since registration itself no longer hands one out.
    private String registerOwnerAndGetToken() throws Exception {
        return registerAndGetToken(registerRequest);
    }

    private String registerAndGetToken(RegisterRestaurantRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String restaurantId = JsonPath.read(result.getResponse().getContentAsString(), "$.restaurant.id");
        Restaurant restaurant = restaurantRepository.findById(UUID.fromString(restaurantId)).orElseThrow();
        restaurant.setApproved(true);
        restaurantRepository.save(restaurant);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(request.getOwnerEmail());
        loginRequest.setPassword(request.getOwnerPassword());
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createTableAndGetId(String token) throws Exception {
        CreateTableRequest request = new CreateTableRequest();
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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

    private String createProductAndGetId(String token, String categoryId, String name, String price) throws Exception {
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

    private List<String> createOrderAndGetItemIds(String token, String tabId, String productId, int itemCount) throws Exception {
        List<CreateOrderItemRequest> items = new java.util.ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            CreateOrderItemRequest item = new CreateOrderItemRequest();
            item.setProductId(UUID.fromString(productId));
            item.setQuantity(1);
            items.add(item);
        }
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(items);

        MvcResult result = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            ids.add(JsonPath.read(result.getResponse().getContentAsString(), "$.items[" + i + "].id"));
        }
        return ids;
    }

    private void updateItemStatus(String token, String itemId, ItemStatus status) throws Exception {
        UpdateOrderItemStatusRequest request = new UpdateOrderItemStatusRequest();
        request.setStatus(status);
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void payTab(String token, String tabId, String paymentMethod, String paidAmount) throws Exception {
        payTab(token, tabId, paymentMethod, paidAmount, "0");
    }

    private void payTab(String token, String tabId, String paymentMethod, String paidAmount, String serviceChargePercentage) throws Exception {
        PaymentEntryRequest entry = new PaymentEntryRequest();
        entry.setPaymentMethod(PaymentMethod.valueOf(paymentMethod));
        entry.setAmount(new BigDecimal(paidAmount));
        RegisterPaymentsRequest request = new RegisterPaymentsRequest();
        request.setPayments(List.of(entry));
        if (serviceChargePercentage != null) {
            request.setServiceChargePercentage(new BigDecimal(serviceChargePercentage));
        }
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void getDashboard_freshRestaurant_shouldReportAllTablesFreeAndNoActivity() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createTableAndGetId(ownerToken);
        createTableAndGetId(ownerToken);
        createTableAndGetId(ownerToken);

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeTables").value(3))
                .andExpect(jsonPath("$.occupiedTables").value(0))
                .andExpect(jsonPath("$.ordersInPreparation").value(0))
                .andExpect(jsonPath("$.revenueToday").value(0))
                // 2026-09-09 onboarding audit, finding #1: tables alone (even several of them)
                // must never flip this to true - only an actual product does.
                .andExpect(jsonPath("$.hasProducts").value(false));
    }

    @Test
    void getDashboard_withAtLeastOneProduct_shouldReportHasProductsTrue() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasProducts").value(true));
    }

    @Test
    void getDashboard_withOpenItems_shouldCountAllNonDeliveredStatuses() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");

        List<String> itemIds = createOrderAndGetItemIds(ownerToken, tabId, productId, 3);
        updateItemStatus(ownerToken, itemIds.get(1), ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemIds.get(2), ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemIds.get(2), ItemStatus.READY);
        // itemIds.get(0) stays PENDING

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTables").value(1))
                .andExpect(jsonPath("$.freeTables").value(0))
                .andExpect(jsonPath("$.ordersInPreparation").value(3));
    }

    @Test
    void getDashboard_afterPayment_shouldReflectRevenueTodayAndFreeTheTable() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");

        List<String> itemIds = createOrderAndGetItemIds(ownerToken, tabId, productId, 1);
        String itemId = itemIds.get(0);
        updateItemStatus(ownerToken, itemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemId, ItemStatus.READY);
        updateItemStatus(ownerToken, itemId, ItemStatus.DELIVERED);
        payTab(ownerToken, tabId, "PIX", "25.90");

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedTables").value(0))
                .andExpect(jsonPath("$.freeTables").value(1))
                .andExpect(jsonPath("$.ordersInPreparation").value(0))
                .andExpect(jsonPath("$.revenueToday").value(25.90));
    }

    @Test
    void getDashboard_withServiceCharge_shouldExcludeItFromRevenueToday() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "100.00");

        List<String> itemIds = createOrderAndGetItemIds(ownerToken, tabId, productId, 1);
        String itemId = itemIds.get(0);
        updateItemStatus(ownerToken, itemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemId, ItemStatus.READY);
        updateItemStatus(ownerToken, itemId, ItemStatus.DELIVERED);
        payTab(ownerToken, tabId, "PIX", "110.00", "10");

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueToday").value(100.00));
    }

    @Test
    void getDashboard_crossTenant_shouldNotLeakOtherRestaurantData() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createTableAndGetId(ownerToken);

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        String otherToken = registerAndGetToken(otherRestaurant);
        createTableAndGetId(otherToken);
        createTableAndGetId(otherToken);

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeTables").value(1));

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeTables").value(2));
    }

    @Test
    void getDashboard_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().is4xxClientError());
    }
}
