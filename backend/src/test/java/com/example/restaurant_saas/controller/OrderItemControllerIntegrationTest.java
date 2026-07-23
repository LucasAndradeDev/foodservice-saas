package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
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
class OrderItemControllerIntegrationTest {

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
        registerRequest.setPhone("11999999999");
        registerRequest.setAddress("Main St, 100");
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

    private String registerAndGetToken(RegisterRestaurantRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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

    private String updateStatusRequestBody(String status) throws Exception {
        UpdateOrderItemStatusRequest request = new UpdateOrderItemStatusRequest();
        request.setStatus(com.example.restaurant_saas.domain.enums.ItemStatus.valueOf(status));
        return objectMapper.writeValueAsString(request);
    }

    private record TestSetup(String ownerToken, String tabId, String productId) {}

    private TestSetup setupTabWithProduct() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");
        return new TestSetup(ownerToken, tabId, productId);
    }

    @Test
    void updateStatus_kitchenMovesPendingToPreparing_shouldSucceed() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String kitchenToken = tokenFor(createUserDirectly(owner, UserRole.KITCHEN));
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("PREPARING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));
    }

    @Test
    void updateStatus_kitchenMovesPreparingToReady_shouldSucceed() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String kitchenToken = tokenFor(createUserDirectly(owner, UserRole.KITCHEN));
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("PREPARING")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("READY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void updateStatus_waiterMarksReadyToDelivered_shouldSucceed() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String kitchenToken = tokenFor(createUserDirectly(owner, UserRole.KITCHEN));
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("PREPARING")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("READY")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("DELIVERED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void updateStatus_kitchenCannotMarkDelivered_shouldBeForbidden() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String kitchenToken = tokenFor(createUserDirectly(owner, UserRole.KITCHEN));
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("PREPARING")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("READY")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("DELIVERED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_waiterCannotStartPreparing_shouldBeForbidden() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("PREPARING")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_skippingStep_shouldReturn400() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("READY")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_fromDelivered_shouldReturn400() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("PREPARING")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("READY")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("DELIVERED")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("CANCELLED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_cancelFromPending_asWaiter_shouldSucceed() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void updateStatus_cancelFromReady_asKitchen_shouldSucceed() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String kitchenToken = tokenFor(createUserDirectly(owner, UserRole.KITCHEN));
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("PREPARING")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("READY")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void updateStatus_nonexistentItem_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        mockMvc.perform(patch("/api/v1/order-items/" + UUID.randomUUID() + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("PREPARING")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_crossTenant_shouldReturn400() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        String otherToken = registerAndGetToken(otherRestaurant);

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("PREPARING")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listKitchenQueue_defaultFilter_shouldExcludeDeliveredAndCancelled() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("PREPARING")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("READY")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("DELIVERED")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/order-items")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listKitchenQueue_filterByStatus_shouldReturnOnlyMatching() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("PREPARING")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/order-items").param("status", "PREPARING")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PREPARING"));

        mockMvc.perform(get("/api/v1/order-items").param("status", "READY")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listKitchenQueue_crossTab_shouldReturnItemsFromAllOpenTabsWithTableNumbers() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");

        String table1 = createTableAndGetId(ownerToken);
        String tab1 = openTabAndGetId(ownerToken, table1);
        createOrderAndGetFirstItemId(ownerToken, tab1, productId);

        String table2 = createTableAndGetId(ownerToken);
        String tab2 = openTabAndGetId(ownerToken, table2);
        createOrderAndGetFirstItemId(ownerToken, tab2, productId);

        mockMvc.perform(get("/api/v1/order-items")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listKitchenQueue_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/order-items"))
                .andExpect(status().is4xxClientError());
    }
}
