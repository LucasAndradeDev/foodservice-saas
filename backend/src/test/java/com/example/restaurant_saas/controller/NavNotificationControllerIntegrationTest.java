package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NavNotificationControllerIntegrationTest {

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

    private User createUserDirectly(User owner, UserRole role) {
        User user = User.builder()
                .restaurant(owner.getRestaurant())
                .name(role.name())
                .email(role.name().toLowerCase() + "+" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(role)
                .active(true)
                .build();
        return userRepository.save(user);
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(new UserDetailsImpl(user));
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

    private String createProductAndGetId(String token, String categoryId) throws Exception {
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

    private String createOrderAndGetFirstItemId(String token, String tabId, String productId) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
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

    private void moveItemToStatus(String token, String itemId, ItemStatus status) throws Exception {
        UpdateOrderItemStatusRequest request = new UpdateOrderItemStatusRequest();
        request.setStatus(status);
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private record TestSetup(String ownerToken, String tabId, String productId) {}

    private TestSetup setupTabWithProduct() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId);
        return new TestSetup(ownerToken, tabId, productId);
    }

    @Test
    void getStatus_newPendingItem_showsKitchenTrue() throws Exception {
        TestSetup setup = setupTabWithProduct();
        createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(get("/api/v1/nav-notifications/status")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kitchen").value(true))
                .andExpect(jsonPath("$.checkout").value(false));
    }

    @Test
    void markSeen_onlyClearsForTheUserWhoSawIt() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        // Both start out seeing the pending kitchen item.
        mockMvc.perform(get("/api/v1/nav-notifications/status")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(jsonPath("$.kitchen").value(true));
        mockMvc.perform(get("/api/v1/nav-notifications/status")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(jsonPath("$.kitchen").value(true));

        // Owner visits Kitchen and marks it seen.
        mockMvc.perform(post("/api/v1/nav-notifications/KITCHEN/seen")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(status().isNoContent());

        // Owner no longer sees the alert, but the waiter — who never looked — still does.
        mockMvc.perform(get("/api/v1/nav-notifications/status")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(jsonPath("$.kitchen").value(false));
        mockMvc.perform(get("/api/v1/nav-notifications/status")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(jsonPath("$.kitchen").value(true));
    }

    @Test
    void getStatus_tabFullyDelivered_showsCheckoutTrueEvenWithoutBillRequest() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        moveItemToStatus(setup.ownerToken(), itemId, ItemStatus.PREPARING);
        moveItemToStatus(setup.ownerToken(), itemId, ItemStatus.READY);

        // Not ready yet — the last item hasn't been delivered.
        mockMvc.perform(get("/api/v1/nav-notifications/status")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(jsonPath("$.checkout").value(false));

        moveItemToStatus(setup.ownerToken(), itemId, ItemStatus.DELIVERED);

        mockMvc.perform(get("/api/v1/nav-notifications/status")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(jsonPath("$.checkout").value(true));
    }
}
