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
import com.example.restaurant_saas.dto.request.PayTabRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
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
class TabControllerIntegrationTest {

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

    private void updateItemStatus(String token, String itemId, ItemStatus status) throws Exception {
        UpdateOrderItemStatusRequest request = new UpdateOrderItemStatusRequest();
        request.setStatus(status);
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private String payTabRequestBody(String paymentMethod, String paidAmount) throws Exception {
        PayTabRequest request = new PayTabRequest();
        request.setPaymentMethod(PaymentMethod.valueOf(paymentMethod));
        request.setPaidAmount(new BigDecimal(paidAmount));
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void payTab_withPendingItems_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");
        createOrderAndGetFirstItemId(ownerToken, tabId, productId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "25.90")))
                .andExpect(status().isForbidden());
    }

    @Test
    void payTab_withAllItemsDeliveredOrCancelled_shouldSucceedAndChargeOnlyDeliveredItems() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");

        String deliveredItemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        updateItemStatus(ownerToken, deliveredItemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, deliveredItemId, ItemStatus.READY);
        updateItemStatus(ownerToken, deliveredItemId, ItemStatus.DELIVERED);

        String cancelledItemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        updateItemStatus(ownerToken, cancelledItemId, ItemStatus.CANCELLED);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "25.90")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.paidAmount").value(25.90))
                .andExpect(jsonPath("$.paidAt").exists());
    }

    @Test
    void payTab_withAmountNotMatchingTotal_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");

        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        updateItemStatus(ownerToken, itemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemId, ItemStatus.READY);
        updateItemStatus(ownerToken, itemId, ItemStatus.DELIVERED);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "10.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void payTab_withEmptyTab_shouldSucceedWithZeroAmount() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("PIX", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void openTab_withOneTable_shouldSucceedAndOccupyTable() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);

        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of(UUID.fromString(tableId)));

        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.tables.length()").value(1))
                .andExpect(jsonPath("$.closedAt").doesNotExist());

        mockMvc.perform(get("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
    }

    @Test
    void openTab_withMultipleTables_shouldLinkAllAndOccupyAll() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String table1 = createTableAndGetId(ownerToken);
        String table2 = createTableAndGetId(ownerToken);

        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of(UUID.fromString(table1), UUID.fromString(table2)));

        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tables.length()").value(2));

        mockMvc.perform(get("/api/v1/tables/" + table1)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
        mockMvc.perform(get("/api/v1/tables/" + table2)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
    }

    @Test
    void openTab_withAlreadyOccupiedTable_shouldFailWithoutSideEffects() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String occupiedTable = createTableAndGetId(ownerToken);
        String freeTable = createTableAndGetId(ownerToken);

        OpenTabRequest firstRequest = new OpenTabRequest();
        firstRequest.setTableIds(List.of(UUID.fromString(occupiedTable)));
        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        OpenTabRequest secondRequest = new OpenTabRequest();
        secondRequest.setTableIds(List.of(UUID.fromString(occupiedTable), UUID.fromString(freeTable)));
        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/tables/" + freeTable)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    void openTab_withNonexistentTable_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openTab_withCrossTenantTable_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        String otherToken = registerAndGetToken(otherRestaurant);
        String otherTable = createTableAndGetId(otherToken);

        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of(UUID.fromString(otherTable)));

        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openTab_withEmptyTableIds_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of());

        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openTab_asWaiter_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);
        String tableId = createTableAndGetId(ownerToken);

        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of(UUID.fromString(tableId)));

        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void openTab_asKitchen_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User kitchen = createUserDirectly(owner, UserRole.KITCHEN);
        String kitchenToken = tokenFor(kitchen);
        String tableId = createTableAndGetId(ownerToken);

        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of(UUID.fromString(tableId)));

        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void payTab_shouldFreeAllLinkedTables() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String table1 = createTableAndGetId(ownerToken);
        String table2 = createTableAndGetId(ownerToken);

        OpenTabRequest openRequest = new OpenTabRequest();
        openRequest.setTableIds(List.of(UUID.fromString(table1), UUID.fromString(table2)));
        MvcResult openResult = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String tabId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").exists());

        mockMvc.perform(get("/api/v1/tables/" + table1)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.status").value("FREE"));
        mockMvc.perform(get("/api/v1/tables/" + table2)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    void payTab_alreadyClosed_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);

        OpenTabRequest openRequest = new OpenTabRequest();
        openRequest.setTableIds(List.of(UUID.fromString(tableId)));
        MvcResult openResult = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String tabId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "0")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void payTab_asKitchen_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User kitchen = createUserDirectly(owner, UserRole.KITCHEN);
        String kitchenToken = tokenFor(kitchen);
        String tableId = createTableAndGetId(ownerToken);

        OpenTabRequest openRequest = new OpenTabRequest();
        openRequest.setTableIds(List.of(UUID.fromString(tableId)));
        MvcResult openResult = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String tabId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "0")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTabs_filterByStatus_shouldReturnOnlyMatching() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String table1 = createTableAndGetId(ownerToken);
        String table2 = createTableAndGetId(ownerToken);

        OpenTabRequest firstRequest = new OpenTabRequest();
        firstRequest.setTableIds(List.of(UUID.fromString(table1)));
        MvcResult firstResult = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String firstTabId = JsonPath.read(firstResult.getResponse().getContentAsString(), "$.id");

        OpenTabRequest secondRequest = new OpenTabRequest();
        secondRequest.setTableIds(List.of(UUID.fromString(table2)));
        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/tabs/" + firstTabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "0")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tabs").param("status", "OPEN")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/tabs").param("status", "CLOSED")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getTab_crossTenant_shouldNotBeFound() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another2+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        String otherToken = registerAndGetToken(otherRestaurant);
        String otherTable = createTableAndGetId(otherToken);

        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of(UUID.fromString(otherTable)));
        MvcResult openResult = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String otherTabId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/tabs/" + otherTabId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listTabs_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/tabs"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void cancelTab_withNoOrders_shouldCloseAndFreeTables() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.paymentMethod").doesNotExist())
                .andExpect(jsonPath("$.paidAmount").doesNotExist());

        mockMvc.perform(get("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    void cancelTab_withOrders_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");
        createOrderAndGetFirstItemId(ownerToken, tabId, productId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelTab_alreadyClosed_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelTab_withoutToken_shouldBeRejected() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void markReceiptPrinted_shouldSetReceiptPrintedAt() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(get("/api/v1/tabs/" + tabId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptPrintedAt").doesNotExist());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/print")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptPrintedAt").exists());
    }

    @Test
    void markReceiptPrinted_calledTwice_shouldSucceedBothTimes() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/print")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptPrintedAt").exists());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/print")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptPrintedAt").exists());
    }

    @Test
    void markReceiptPrinted_asKitchen_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User kitchen = createUserDirectly(owner, UserRole.KITCHEN);
        String kitchenToken = tokenFor(kitchen);
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/print")
                        .header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void markReceiptPrinted_crossTenant_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another3+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        String otherToken = registerAndGetToken(otherRestaurant);
        String otherTable = createTableAndGetId(otherToken);

        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of(UUID.fromString(otherTable)));
        MvcResult openResult = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String otherTabId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/v1/tabs/" + otherTabId + "/print")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }
}
