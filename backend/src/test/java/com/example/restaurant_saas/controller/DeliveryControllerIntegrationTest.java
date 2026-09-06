package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.CourierVehicleType;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateDeliveryZoneRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateUserRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateDeliveryStatusRequest;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.support.TenantTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DeliveryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    // A courier is a User with role COURIER now (task 28 redesign) - created through the same
    // invite endpoint as any other staff member, just with the extra phone/vehicleType fields.
    private String createCourier(String token, String name) throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setName(name);
        request.setEmail("courier+" + System.nanoTime() + "@test.com");
        request.setRole(UserRole.COURIER);
        request.setPhone("11988887777");
        request.setVehicleType(CourierVehicleType.MOTORCYCLE);
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private record OwnerSession(String token, User user) {
    }

    // Same registration flow as registerOwnerAndGetToken, but also hands back the persisted owner
    // User row (via a bypass-RLS lookup by the email we just chose) so a courier's User row can be
    // built directly against the same restaurant, without going through the invite-email flow.
    private OwnerSession registerOwnerAndGetSession(String restaurantName) throws Exception {
        String email = "owner+" + System.nanoTime() + "@test.com";
        RegisterRestaurantRequest registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName(restaurantName);
        registerRequest.setOwnerName("Owner");
        registerRequest.setOwnerEmail(email);
        registerRequest.setOwnerPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        User owner = userRepository.findByEmailBypassingRls(email).orElseThrow();
        return new OwnerSession(token, owner);
    }

    // Bypasses the invite-email flow (already covered by UserControllerIntegrationTest) - these
    // tests only care about a courier's own self-service screen, so a directly-persisted courier
    // User row with a directly-generated JWT is enough to act as them.
    private User createCourierDirectly(User owner, String name) {
        User user = User.builder()
                .restaurant(owner.getRestaurant())
                .name(name)
                .email("courier+" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(UserRole.COURIER)
                .active(true)
                .phone("11988887777")
                .vehicleType(CourierVehicleType.MOTORCYCLE)
                .build();
        return TenantTestSupport.withTenant(owner.getRestaurant().getId(), () -> userRepository.save(user));
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(new UserDetailsImpl(user));
    }

    private record DeliveryTab(String tabId, String itemId, String accessToken) {
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
        return new DeliveryTab(
                JsonPath.read(content, "$.tabId"),
                JsonPath.read(content, "$.order.items[0].id"),
                JsonPath.read(content, "$.accessToken"));
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
    // Cheeseburger (25.90, see createProduct) plus the Centro zone's fee (8.00, see
    // createDeliveryZone): 25.90 + 8.00 = 33.90. No service charge on delivery orders (2026-08-18
    // decision, TabService#resolveBillTotal).
    private void payTabInFull(String token, String tabId) throws Exception {
        ObjectNode payment = objectMapper.createObjectNode();
        payment.put("paymentMethod", "PIX");
        payment.put("amount", new BigDecimal("33.90"));
        ObjectNode body = objectMapper.createObjectNode();
        body.set("payments", objectMapper.valueToTree(List.of(payment)));

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private void assignCourierToTab(String token, String tabId, String courierId) throws Exception {
        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/courier")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":\"" + courierId + "\"}"))
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
        assignCourierToTab(token, tabId, createCourier(token, "Joao Motoboy"));

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
        assignCourierToTab(token, deliveredTabId, createCourier(token, "Joao Motoboy"));

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
        assignCourierToTab(token, tabId, createCourier(token, "Joao Motoboy"));

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
        assignCourierToTab(token, tab.tabId(), createCourier(token, "Joao Motoboy"));

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
        assignCourierToTab(token, tab.tabId(), createCourier(token, "Joao Motoboy"));

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

    @Test
    void updateStatus_toOutForDeliveryWithoutCourier_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");
        String slug = getSlug(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        createDeliveryZone(token);
        DeliveryTab tab = createDeliveryTab(token, slug, productId, "11999990022");
        payTabInFull(token, tab.tabId());
        markItemReady(token, tab.itemId());

        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isBadRequest());

        assignCourierToTab(token, tab.tabId(), createCourier(token, "Joao Motoboy"));

        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());
    }

    @Test
    void assignCourier_afterOutForDelivery_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");
        String slug = getSlug(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        createDeliveryZone(token);
        DeliveryTab tab = createDeliveryTab(token, slug, productId, "11999990023");
        String tabId = tab.tabId();
        payTabInFull(token, tabId);
        markItemReady(token, tab.itemId());
        String courierId = createCourier(token, "Joao Motoboy");
        assignCourierToTab(token, tabId, courierId);

        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());

        String otherCourierId = createCourier(token, "Pedro Motoboy");
        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/courier")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":\"" + otherCourierId + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/courier")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":null}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/deliveries")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courierId").value(courierId));
    }

    @Test
    void assignCourier_thenUnassign_shouldSucceed() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");
        String slug = getSlug(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        createDeliveryZone(token);
        String tabId = createDeliveryTab(token, slug, productId, "11999990020").tabId();
        String courierId = createCourier(token, "Joao Motoboy");

        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/courier")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":\"" + courierId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(courierId))
                .andExpect(jsonPath("$.courierName").value("Joao Motoboy"));

        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/courier")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(nullValue()));
    }

    @Test
    void assignCourier_fromAnotherRestaurant_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");
        String slug = getSlug(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        createDeliveryZone(token);
        String tabId = createDeliveryTab(token, slug, productId, "11999990021").tabId();

        String otherToken = registerOwnerAndGetToken("Burger House Other");
        String otherCourierId = createCourier(otherToken, "Courier From Other Restaurant");

        mockMvc.perform(patch("/api/v1/deliveries/" + tabId + "/courier")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":\"" + otherCourierId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_courierMarksOwnDeliveryAsDelivered_shouldSucceed() throws Exception {
        OwnerSession owner = registerOwnerAndGetSession("Burger House");
        String slug = getSlug(owner.token());
        String categoryId = createCategory(owner.token());
        String productId = createProduct(owner.token(), categoryId);
        createDeliveryZone(owner.token());
        DeliveryTab tab = createDeliveryTab(owner.token(), slug, productId, "11999990030");
        payTabInFull(owner.token(), tab.tabId());
        markItemReady(owner.token(), tab.itemId());

        User courier = createCourierDirectly(owner.user(), "Joao Motoboy");
        assignCourierToTab(owner.token(), tab.tabId(), courier.getId().toString());
        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + tokenFor(courier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("DELIVERED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void updateStatus_courierMarksAnotherCouriersDelivery_shouldReturn403() throws Exception {
        OwnerSession owner = registerOwnerAndGetSession("Burger House");
        String slug = getSlug(owner.token());
        String categoryId = createCategory(owner.token());
        String productId = createProduct(owner.token(), categoryId);
        createDeliveryZone(owner.token());
        DeliveryTab tab = createDeliveryTab(owner.token(), slug, productId, "11999990031");
        payTabInFull(owner.token(), tab.tabId());
        markItemReady(owner.token(), tab.itemId());

        User assignedCourier = createCourierDirectly(owner.user(), "Joao Motoboy");
        User otherCourier = createCourierDirectly(owner.user(), "Pedro Motoboy");
        assignCourierToTab(owner.token(), tab.tabId(), assignedCourier.getId().toString());
        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + tokenFor(otherCourier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("DELIVERED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMyDeliveries_scopedToOwnCourierAndOutForDeliveryOnly_shouldSucceed() throws Exception {
        OwnerSession owner = registerOwnerAndGetSession("Burger House");
        String slug = getSlug(owner.token());
        String categoryId = createCategory(owner.token());
        String productId = createProduct(owner.token(), categoryId);
        createDeliveryZone(owner.token());

        User courier = createCourierDirectly(owner.user(), "Joao Motoboy");
        User otherCourier = createCourierDirectly(owner.user(), "Pedro Motoboy");

        // Own order, still SEPARATING (assigned but not dispatched yet) - must not show up.
        DeliveryTab notDispatched = createDeliveryTab(owner.token(), slug, productId, "11999990032");
        assignCourierToTab(owner.token(), notDispatched.tabId(), courier.getId().toString());

        // Own order, dispatched - must show up.
        DeliveryTab dispatched = createDeliveryTab(owner.token(), slug, productId, "11999990033");
        payTabInFull(owner.token(), dispatched.tabId());
        markItemReady(owner.token(), dispatched.itemId());
        assignCourierToTab(owner.token(), dispatched.tabId(), courier.getId().toString());
        mockMvc.perform(patch("/api/v1/deliveries/" + dispatched.tabId() + "/status")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());

        // Another courier's dispatched order - must not show up.
        DeliveryTab othersOrder = createDeliveryTab(owner.token(), slug, productId, "11999990034");
        payTabInFull(owner.token(), othersOrder.tabId());
        markItemReady(owner.token(), othersOrder.itemId());
        assignCourierToTab(owner.token(), othersOrder.tabId(), otherCourier.getId().toString());
        mockMvc.perform(patch("/api/v1/deliveries/" + othersOrder.tabId() + "/status")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/deliveries/mine")
                        .header("Authorization", "Bearer " + tokenFor(courier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tabId").value(dispatched.tabId()));
    }

    @Test
    void courierRole_cannotListAllDeliveriesOrAssignCourier_shouldReturn403() throws Exception {
        OwnerSession owner = registerOwnerAndGetSession("Burger House");
        User courier = createCourierDirectly(owner.user(), "Joao Motoboy");

        mockMvc.perform(get("/api/v1/deliveries")
                        .header("Authorization", "Bearer " + tokenFor(courier)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/deliveries/couriers")
                        .header("Authorization", "Bearer " + tokenFor(courier)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/deliveries/" + UUID.randomUUID() + "/courier")
                        .header("Authorization", "Bearer " + tokenFor(courier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":null}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMyLocation_shouldShowUpInLiveCouriersRegardlessOfActiveDelivery() throws Exception {
        OwnerSession owner = registerOwnerAndGetSession("Burger House");
        User courier = createCourierDirectly(owner.user(), "Joao Motoboy");

        mockMvc.perform(patch("/api/v1/deliveries/mine/location")
                        .header("Authorization", "Bearer " + tokenFor(courier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":-23.5505,\"longitude\":-46.6333}"))
                .andExpect(status().isNoContent());

        // No active delivery at all - still shows up, so staff can see who's free/nearby.
        mockMvc.perform(get("/api/v1/deliveries/couriers/live")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(courier.getId().toString()))
                .andExpect(jsonPath("$[0].latitude").value(-23.5505))
                .andExpect(jsonPath("$[0].longitude").value(-46.6333))
                .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    void listLiveCouriers_marksCourierUnavailableWhileOutForDelivery() throws Exception {
        OwnerSession owner = registerOwnerAndGetSession("Burger House");
        String slug = getSlug(owner.token());
        String categoryId = createCategory(owner.token());
        String productId = createProduct(owner.token(), categoryId);
        createDeliveryZone(owner.token());

        User busyCourier = createCourierDirectly(owner.user(), "Joao Motoboy");
        busyCourier.setLatitude(-23.55);
        busyCourier.setLongitude(-46.63);
        busyCourier.setLocationUpdatedAt(OffsetDateTime.now());
        TenantTestSupport.withTenant(owner.user().getRestaurant().getId(), () -> { userRepository.save(busyCourier); });

        User freeCourier = createCourierDirectly(owner.user(), "Pedro Motoboy");
        freeCourier.setLatitude(-23.56);
        freeCourier.setLongitude(-46.64);
        freeCourier.setLocationUpdatedAt(OffsetDateTime.now());
        TenantTestSupport.withTenant(owner.user().getRestaurant().getId(), () -> { userRepository.save(freeCourier); });

        DeliveryTab tab = createDeliveryTab(owner.token(), slug, productId, "11999990050");
        assignCourierToTab(owner.token(), tab.tabId(), busyCourier.getId().toString());
        payTabInFull(owner.token(), tab.tabId());
        markItemReady(owner.token(), tab.itemId());
        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/deliveries/couriers/live")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id=='" + busyCourier.getId() + "')].available").value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$[?(@.id=='" + freeCourier.getId() + "')].available").value(org.hamcrest.Matchers.contains(true)));
    }

    @Test
    void updateMyLocation_nonCourierRole_shouldReturn403() throws Exception {
        String token = registerOwnerAndGetToken("Burger House");

        mockMvc.perform(patch("/api/v1/deliveries/mine/location")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":-23.5505,\"longitude\":-46.6333}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMyLocation_outOfRangeCoordinates_shouldReturn400() throws Exception {
        OwnerSession owner = registerOwnerAndGetSession("Burger House");
        User courier = createCourierDirectly(owner.user(), "Joao Motoboy");

        mockMvc.perform(patch("/api/v1/deliveries/mine/location")
                        .header("Authorization", "Bearer " + tokenFor(courier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":-91,\"longitude\":-46.6333}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listLiveCouriers_excludesStaleAndNeverReportedAndOtherRestaurants() throws Exception {
        OwnerSession owner = registerOwnerAndGetSession("Burger House");

        User online = createCourierDirectly(owner.user(), "Joao Motoboy");
        online.setLatitude(-23.55);
        online.setLongitude(-46.63);
        online.setLocationUpdatedAt(OffsetDateTime.now());
        TenantTestSupport.withTenant(owner.user().getRestaurant().getId(), () -> { userRepository.save(online); });

        // Reported a position once, but it's stale - the 5-minute window has passed.
        User stale = createCourierDirectly(owner.user(), "Pedro Motoboy");
        stale.setLatitude(-23.55);
        stale.setLongitude(-46.63);
        stale.setLocationUpdatedAt(OffsetDateTime.now().minusMinutes(10));
        TenantTestSupport.withTenant(owner.user().getRestaurant().getId(), () -> { userRepository.save(stale); });

        // Never reported a position at all.
        createCourierDirectly(owner.user(), "Carlos Motoboy");

        OwnerSession otherOwner = registerOwnerAndGetSession("Burger House Other");
        User otherRestaurantCourier = createCourierDirectly(otherOwner.user(), "Courier From Other Restaurant");
        otherRestaurantCourier.setLatitude(-23.55);
        otherRestaurantCourier.setLongitude(-46.63);
        otherRestaurantCourier.setLocationUpdatedAt(OffsetDateTime.now());
        TenantTestSupport.withTenant(otherOwner.user().getRestaurant().getId(), () -> { userRepository.save(otherRestaurantCourier); });

        mockMvc.perform(get("/api/v1/deliveries/couriers/live")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(online.getId().toString()));
    }

    @Test
    void getByAccessToken_publicResponse_hasFuzzedCourierLocationOnlyWhenOutForDeliveryAndFresh() throws Exception {
        OwnerSession owner = registerOwnerAndGetSession("Burger House");
        String slug = getSlug(owner.token());
        String categoryId = createCategory(owner.token());
        String productId = createProduct(owner.token(), categoryId);
        createDeliveryZone(owner.token());
        DeliveryTab tab = createDeliveryTab(owner.token(), slug, productId, "11999990040");

        // Still SEPARATING - no pin yet, even though a courier is already assigned with a position.
        User courier = createCourierDirectly(owner.user(), "Joao Motoboy");
        assignCourierToTab(owner.token(), tab.tabId(), courier.getId().toString());
        courier.setLatitude(-23.550519);
        courier.setLongitude(-46.633309);
        courier.setLocationUpdatedAt(OffsetDateTime.now());
        TenantTestSupport.withTenant(owner.user().getRestaurant().getId(), () -> { userRepository.save(courier); });

        mockMvc.perform(get("/api/v1/public/deliveries/" + tab.accessToken() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEPARATING"))
                .andExpect(jsonPath("$.courierLatitude").value(nullValue()))
                .andExpect(jsonPath("$.courierLongitude").value(nullValue()));

        payTabInFull(owner.token(), tab.tabId());
        markItemReady(owner.token(), tab.itemId());
        mockMvc.perform(patch("/api/v1/deliveries/" + tab.tabId() + "/status")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest("OUT_FOR_DELIVERY"))))
                .andExpect(status().isOk());

        // Now OUT_FOR_DELIVERY with a fresh position - rounded to 3 decimal places on the public
        // path, not the exact value the courier reported (-23.550519 / -46.633309).
        mockMvc.perform(get("/api/v1/public/deliveries/" + tab.accessToken() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierLatitude").value(-23.551))
                .andExpect(jsonPath("$.courierLongitude").value(-46.633));

        // Staff's own view of the same order gets the exact, unrounded position.
        mockMvc.perform(get("/api/v1/deliveries")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courierLatitude").value(-23.550519))
                .andExpect(jsonPath("$[0].courierLongitude").value(-46.633309));
    }

    private UpdateDeliveryStatusRequest statusRequest(String status) {
        UpdateDeliveryStatusRequest request = new UpdateDeliveryStatusRequest();
        request.setStatus(com.example.restaurant_saas.domain.enums.DeliveryStatus.valueOf(status));
        return request;
    }
}
