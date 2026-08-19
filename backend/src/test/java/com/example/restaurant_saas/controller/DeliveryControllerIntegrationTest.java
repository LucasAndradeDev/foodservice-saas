package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateDeliveryZoneRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateDeliveryStatusRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DeliveryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerOwnerAndGetToken(String restaurantName) throws Exception {
        RegisterRestaurantRequest registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName(restaurantName);
        registerRequest.setOwnerName("Owner");
        registerRequest.setOwnerEmail("owner+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");

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

    private String createCategory(String token) throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Burgers");
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createProduct(String token, String categoryId) throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
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

    private void createDeliveryZone(String token) throws Exception {
        CreateDeliveryZoneRequest request = new CreateDeliveryZoneRequest();
        request.setNeighborhood("Centro");
        request.setFee(new BigDecimal("8.00"));
        mockMvc.perform(post("/api/v1/delivery-zones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private record DeliveryTab(String tabId, String itemId) {
    }

    private DeliveryTab createDeliveryTab(String token, String slug, String productId, String phone) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);

        ObjectNode body = objectMapper.createObjectNode();
        body.set("items", objectMapper.valueToTree(List.of(item)));
        body.put("customerName", "Maria Souza");
        body.put("customerPhone", phone);
        body.put("street", "Rua das Flores");
        body.put("number", "123");
        body.put("neighborhood", "Centro");
        body.put("city", "Sao Paulo");

        MvcResult result = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/delivery/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return new DeliveryTab(JsonPath.read(content, "$.tabId"), JsonPath.read(content, "$.order.items[0].id"));
    }

    // Kitchen must finish (READY) before a delivery order is allowed to move OUT_FOR_DELIVERY -
    // mirrors the flow KITCHEN would drive via /api/v1/order-items/{id}/status.
    private void markItemReady(String token, String itemId) throws Exception {
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isOk());
    }

    // Registers a manual full payment (staff PIX entry) so the tab closes (task 29.1 gate) - the
    // charge-gateway flow itself is covered separately, this just needs the tab to become paid.
    // registerPayments computes the total itself (it's not frozen yet on a fresh delivery tab, so
    // GET /tabs/{id}'s remainingBalance would come back null) - every test tab here is one
    // Cheeseburger (25.90, see createProduct) plus the restaurant's default 10% service charge
    // plus the Centro zone's fee (8.00, see createDeliveryZone): 25.90 * 1.10 + 8.00 = 36.49.
    private void payTabInFull(String token, String tabId) throws Exception {
        ObjectNode payment = objectMapper.createObjectNode();
        payment.put("paymentMethod", "PIX");
        payment.put("amount", new BigDecimal("36.49"));
        ObjectNode body = objectMapper.createObjectNode();
        body.set("payments", objectMapper.valueToTree(List.of(payment)));

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private String createOrdinaryTab(String token) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("tableIds", objectMapper.valueToTree(List.of()));
        MvcResult result = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void updateStatus_followingTheFlow_shouldSucceed() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");
        String slug = getSlug(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        createDeliveryZone(token);
        DeliveryTab tab = createDeliveryTab(token, slug, productId, "11999990001");
        String tabId = tab.tabId();
        payTabInFull(token, tabId);
        markItemReady(token, tab.itemId());

        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.tabId").value(tabId));

        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("DELIVERED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void listOpenDeliveries_shouldExcludeDeliveredAndOtherRestaurants() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");
        String slug = getSlug(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        createDeliveryZone(token);

        String openTabId = createDeliveryTab(token, slug, productId, "11999990010").tabId();
        DeliveryTab deliveredTab = createDeliveryTab(token, slug, productId, "11999990011");
        String deliveredTabId = deliveredTab.tabId();
        payTabInFull(token, deliveredTabId);
        markItemReady(token, deliveredTab.itemId());

        mockMvc.perform(patch("/api/v1/deliveries/" + deliveredTabId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/deliveries/" + deliveredTabId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("DELIVERED"))))
                .andExpect(status().isOk());

        String otherToken = registerOwnerAndGetToken("Burger House Other");
        String otherSlug = getSlug(otherToken);
        String otherCategoryId = createCategory(otherToken);
        String otherProductId = createProduct(otherToken, otherCategoryId);
        createDeliveryZone(otherToken);
        createDeliveryTab(otherToken, otherSlug, otherProductId, "11999990012");

        mockMvc.perform(get("/api/v1/deliveries")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tabId").value(openTabId))
                .andExpect(jsonPath("$[0].status").value("SEPARATING"));
    }

    @Test
    void updateStatus_skippingAStep_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");
        String slug = getSlug(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        createDeliveryZone(token);
        String tabId = createDeliveryTab(token, slug, productId, "11999990002").tabId();

        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("DELIVERED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_goingBackwards_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");
        String slug = getSlug(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        createDeliveryZone(token);
        DeliveryTab tab = createDeliveryTab(token, slug, productId, "11999990003");
        String tabId = tab.tabId();
        payTabInFull(token, tabId);
        markItemReady(token, tab.itemId());

        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("SEPARATING"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_kitchenStillPreparing_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");
        String slug = getSlug(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        createDeliveryZone(token);
        DeliveryTab tab = createDeliveryTab(token, slug, productId, "11999990013");
        payTabInFull(token, tab.tabId());

        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/deliveries")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kitchenReady").value(false));

        markItemReady(token, tab.itemId());

        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());
    }

    // 2026-08-18 decision (docs/DELIVERY.md): an unpaid delivery order's item is kept out of the
    // kitchen queue entirely, and can't be moved even by a direct call - no food prepped for an
    // order that might never get paid. Once paid, it appears and behaves like any other item.
    @Test
    void updateStatus_notPaidYet_shouldReturn400AndHideItemFromKitchenQueue() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");
        String slug = getSlug(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        createDeliveryZone(token);
        DeliveryTab tab = createDeliveryTab(token, slug, productId, "11999990014");

        mockMvc.perform(get("/api/v1/order-items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // IllegalStateException (not IllegalArgumentException) - same 403 the role-mismatch gate
        // right above it in updateStatus uses, since "not paid yet" is a permission-shaped refusal
        // too, not a malformed-request one.
        mockMvc.perform(patch("/api/v1/order-items/" + tab.itemId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/deliveries")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paid").value(false));

        payTabInFull(token, tab.tabId());

        mockMvc.perform(get("/api/v1/order-items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        markItemReady(token, tab.itemId());

        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatus_forAnotherRestaurantsTab_shouldReturn400() throws Exception {
        String tokenA = registerOwnerAndGetToken("Burger House A");
        String slugA = getSlug(tokenA);
        String categoryIdA = createCategory(tokenA);
        String productIdA = createProduct(tokenA, categoryIdA);
        createDeliveryZone(tokenA);
        String tabIdA = createDeliveryTab(tokenA, slugA, productIdA, "11999990004").tabId();

        String tokenB = registerOwnerAndGetToken("Burger House B");

        mockMvc.perform(patch("/api/v1/deliveries/" + tabIdA + "/status")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_forTabWithoutDeliveryDetails_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");
        String tabId = createOrdinaryTab(token);

        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isBadRequest());
    }

    private UpdateDeliveryStatusRequest statusRequest(String status) {
        UpdateDeliveryStatusRequest request = new UpdateDeliveryStatusRequest();
        request.setStatus(com.example.restaurant_saas.domain.enums.DeliveryStatus.valueOf(status));
        return request;
    }
}
