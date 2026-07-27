package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.AddTableToTabRequest;
import com.example.restaurant_saas.dto.request.ApplyDiscountRequest;
import com.example.restaurant_saas.dto.request.CancelPaymentRequest;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.MergeTabRequest;
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
        return payTabRequestBody(paymentMethod, paidAmount, null);
    }

    private String payTabRequestBody(String paymentMethod, String paidAmount, String serviceChargePercentage) throws Exception {
        PayTabRequest request = new PayTabRequest();
        request.setPaymentMethod(PaymentMethod.valueOf(paymentMethod));
        request.setPaidAmount(new BigDecimal(paidAmount));
        if (serviceChargePercentage != null) {
            request.setServiceChargePercentage(new BigDecimal(serviceChargePercentage));
        }
        return objectMapper.writeValueAsString(request);
    }

    private String cancelPaymentRequestBody(String reason, String paymentMethod, String paidAmount) throws Exception {
        return cancelPaymentRequestBody(reason, paymentMethod, paidAmount, null);
    }

    private String cancelPaymentRequestBody(String reason, String paymentMethod, String paidAmount, String serviceChargePercentage) throws Exception {
        CancelPaymentRequest request = new CancelPaymentRequest();
        request.setReason(reason);
        if (paymentMethod != null) {
            request.setPaymentMethod(PaymentMethod.valueOf(paymentMethod));
        }
        if (paidAmount != null) {
            request.setPaidAmount(new BigDecimal(paidAmount));
        }
        if (serviceChargePercentage != null) {
            request.setServiceChargePercentage(new BigDecimal(serviceChargePercentage));
        }
        return objectMapper.writeValueAsString(request);
    }

    private String discountRequestBody(DiscountType type, String value, String reason) throws Exception {
        ApplyDiscountRequest request = new ApplyDiscountRequest();
        request.setDiscountType(type);
        if (value != null) {
            request.setDiscountValue(new BigDecimal(value));
        }
        request.setReason(reason);
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
    void openTab_withNullTableIds_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
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

    @Test
    void openTab_withoutTables_shouldCreateCounterTabWithEmptyTablesList() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of());

        mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.tables").isArray())
                .andExpect(jsonPath("$.tables").isEmpty());
    }

    @Test
    void openTab_withoutTables_thenOrderAndPay_shouldWorkEndToEnd() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");

        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of());
        MvcResult openResult = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String tabId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");

        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        updateItemStatus(ownerToken, itemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemId, ItemStatus.READY);
        updateItemStatus(ownerToken, itemId, ItemStatus.DELIVERED);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "25.90")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.tables").isEmpty());
    }

    private String addTableRequestBody(String tableId) throws Exception {
        AddTableToTabRequest request = new AddTableToTabRequest();
        request.setTableId(UUID.fromString(tableId));
        return objectMapper.writeValueAsString(request);
    }

    private String mergeTabRequestBody(String sourceTabId) throws Exception {
        MergeTabRequest request = new MergeTabRequest();
        request.setSourceTabId(UUID.fromString(sourceTabId));
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void addTable_toFreeTable_shouldOccupyItAndAppendToTab() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String table1 = createTableAndGetId(ownerToken);
        String table2 = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, table1);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addTableRequestBody(table2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables.length()").value(2));

        mockMvc.perform(get("/api/v1/tables/" + table2)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
    }

    @Test
    void addTable_alreadyOccupied_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String table1 = createTableAndGetId(ownerToken);
        String table2 = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, table1);
        openTabAndGetId(ownerToken, table2);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addTableRequestBody(table2)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addTable_toClosedTab_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String table1 = createTableAndGetId(ownerToken);
        String table2 = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, table1);
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addTableRequestBody(table2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mergeTab_shouldMoveTablesAndOrdersAndMarkSourceMerged() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String table1 = createTableAndGetId(ownerToken);
        String table2 = createTableAndGetId(ownerToken);
        String targetTabId = openTabAndGetId(ownerToken, table1);
        String sourceTabId = openTabAndGetId(ownerToken, table2);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");
        createOrderAndGetFirstItemId(ownerToken, targetTabId, productId);
        createOrderAndGetFirstItemId(ownerToken, sourceTabId, productId);

        mockMvc.perform(patch("/api/v1/tabs/" + targetTabId + "/merge")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeTabRequestBody(sourceTabId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetTabId))
                .andExpect(jsonPath("$.tables.length()").value(2));

        mockMvc.perform(get("/api/v1/tabs/" + targetTabId + "/orders")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/tabs/" + sourceTabId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MERGED"))
                .andExpect(jsonPath("$.tables.length()").value(1));

        mockMvc.perform(get("/api/v1/tables/" + table2)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.status").value("OCCUPIED"));

        mockMvc.perform(get("/api/v1/tabs").param("status", "OPEN")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void mergeTab_intoItself_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/merge")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeTabRequestBody(tabId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mergeTab_withClosedSource_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String table1 = createTableAndGetId(ownerToken);
        String table2 = createTableAndGetId(ownerToken);
        String targetTabId = openTabAndGetId(ownerToken, table1);
        String sourceTabId = openTabAndGetId(ownerToken, table2);
        mockMvc.perform(patch("/api/v1/tabs/" + sourceTabId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + targetTabId + "/merge")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeTabRequestBody(sourceTabId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unmergeTab_shouldRestoreSourceAndMoveTablesAndOrdersBack() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String table1 = createTableAndGetId(ownerToken);
        String table2 = createTableAndGetId(ownerToken);
        String targetTabId = openTabAndGetId(ownerToken, table1);
        String sourceTabId = openTabAndGetId(ownerToken, table2);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");
        createOrderAndGetFirstItemId(ownerToken, targetTabId, productId);
        createOrderAndGetFirstItemId(ownerToken, sourceTabId, productId);

        mockMvc.perform(patch("/api/v1/tabs/" + targetTabId + "/merge")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeTabRequestBody(sourceTabId)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + targetTabId + "/unmerge")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeTabRequestBody(sourceTabId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetTabId))
                .andExpect(jsonPath("$.tables.length()").value(1));

        mockMvc.perform(get("/api/v1/tabs/" + targetTabId + "/orders")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/tabs/" + sourceTabId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andExpect(jsonPath("$.tables.length()").value(1));

        mockMvc.perform(get("/api/v1/tabs/" + sourceTabId + "/orders")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/tables/" + table2)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
    }

    @Test
    void unmergeTab_whenTargetNotOpen_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String table1 = createTableAndGetId(ownerToken);
        String table2 = createTableAndGetId(ownerToken);
        String targetTabId = openTabAndGetId(ownerToken, table1);
        String sourceTabId = openTabAndGetId(ownerToken, table2);

        mockMvc.perform(patch("/api/v1/tabs/" + targetTabId + "/merge")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeTabRequestBody(sourceTabId)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + targetTabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "0")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + targetTabId + "/unmerge")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeTabRequestBody(sourceTabId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unmergeTab_whenSourceWasNeverMerged_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String table1 = createTableAndGetId(ownerToken);
        String table2 = createTableAndGetId(ownerToken);
        String targetTabId = openTabAndGetId(ownerToken, table1);
        String otherTabId = openTabAndGetId(ownerToken, table2);

        mockMvc.perform(patch("/api/v1/tabs/" + targetTabId + "/unmerge")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeTabRequestBody(otherTabId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyDiscount_fixedAmount_shouldReduceAmountDueAtPayment() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        updateItemStatus(ownerToken, itemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemId, ItemStatus.READY);
        updateItemStatus(ownerToken, itemId, ItemStatus.DELIVERED);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/discount")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.FIXED, "10.90", "Cortesia do dono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountType").value("FIXED"))
                .andExpect(jsonPath("$.discountReason").value("Cortesia do dono"))
                .andExpect(jsonPath("$.discountAppliedBy").value("Owner"));

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "15.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(15.00));
    }

    @Test
    void applyDiscount_withNullType_shouldClearExistingDiscount() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/discount")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.PERCENTAGE, "10", "Promo")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/discount")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountType").doesNotExist())
                .andExpect(jsonPath("$.discountReason").doesNotExist());
    }

    @Test
    void applyDiscount_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/discount")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.FIXED, "5.00", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void applyDiscount_onClosedTab_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/discount")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.FIXED, "5.00", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyDiscount_fixedAboveItemsTotal_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "25.90");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        updateItemStatus(ownerToken, itemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemId, ItemStatus.READY);
        updateItemStatus(ownerToken, itemId, ItemStatus.DELIVERED);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/discount")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.FIXED, "999.00", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void payTab_withServiceCharge_shouldAddPercentageOnTopOfTotal() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "100.00");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        updateItemStatus(ownerToken, itemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemId, ItemStatus.READY);
        updateItemStatus(ownerToken, itemId, ItemStatus.DELIVERED);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "110.00", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(110.00))
                .andExpect(jsonPath("$.serviceChargePercentage").value(10))
                .andExpect(jsonPath("$.serviceChargeAmount").value(10.00));
    }

    @Test
    void payTab_withServiceChargeOnDiscountedTotal_shouldComputeOverPostDiscountAmount() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "100.00");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        updateItemStatus(ownerToken, itemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemId, ItemStatus.READY);
        updateItemStatus(ownerToken, itemId, ItemStatus.DELIVERED);

        // 20% tab discount -> 80.00, then 10% service charge on top -> 88.00
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/discount")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.PERCENTAGE, "20", null)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "88.00", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(88.00))
                .andExpect(jsonPath("$.serviceChargeAmount").value(8.00));
    }

    @Test
    void payTab_withoutServiceCharge_shouldLeaveFieldsAbsent() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceChargePercentage").doesNotExist())
                .andExpect(jsonPath("$.serviceChargeAmount").doesNotExist());
    }

    @Test
    void payTab_withServiceChargeOver100_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "100.00");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        updateItemStatus(ownerToken, itemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemId, ItemStatus.READY);
        updateItemStatus(ownerToken, itemId, ItemStatus.DELIVERED);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "200.00", "150")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void payTab_asWaiter_cannotWaiveOrOverrideServiceCharge_alwaysUsesRestaurantDefault() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "100.00");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        updateItemStatus(ownerToken, itemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemId, ItemStatus.READY);
        updateItemStatus(ownerToken, itemId, ItemStatus.DELIVERED);

        // Restaurant default is 10% (enabled). Waiter tries to waive it by sending 0 and paying only the items total.
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "100.00", "0")))
                .andExpect(status().isBadRequest());

        // Paying the amount that includes the forced 10% default succeeds, regardless of what was requested.
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "110.00", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceChargePercentage").value(10))
                .andExpect(jsonPath("$.serviceChargeAmount").value(10.00));
    }

    @Test
    void payTab_withServiceChargeAmountMismatch_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        String categoryId = createCategoryAndGetId(ownerToken);
        String productId = createProductAndGetId(ownerToken, categoryId, "Cheeseburger", "100.00");
        String itemId = createOrderAndGetFirstItemId(ownerToken, tabId, productId);
        updateItemStatus(ownerToken, itemId, ItemStatus.PREPARING);
        updateItemStatus(ownerToken, itemId, ItemStatus.READY);
        updateItemStatus(ownerToken, itemId, ItemStatus.DELIVERED);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "100.00", "10")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelPayment_asOwner_shouldCorrectPaymentAndStayClosed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel-payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPaymentRequestBody("Registrei a forma errada", "PIX", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").exists())
                .andExpect(jsonPath("$.paymentMethod").value("PIX"))
                .andExpect(jsonPath("$.paidAmount").value(0))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.paymentCancelledBy").value("Owner"))
                .andExpect(jsonPath("$.paymentCancelledAt").exists())
                .andExpect(jsonPath("$.paymentCancelReason").value("Registrei a forma errada"));

        // The table was already freed by the original payment; correcting the payment record
        // doesn't touch it either way.
        mockMvc.perform(get("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    void cancelPayment_worksEvenWhenTableIsNowOccupiedByAnotherTab() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "0")))
                .andExpect(status().isOk());

        // A new group sits down at the same table before the mistake is noticed.
        openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel-payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPaymentRequestBody("Registrei a forma errada", "PIX", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.paymentMethod").value("PIX"));

        // Untouched: still belongs to the new group's tab.
        mockMvc.perform(get("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
    }

    @Test
    void cancelPayment_withAmountNotMatchingTotal_shouldReturn400() throws Exception {
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
                        .content(payTabRequestBody("CASH", "25.90")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel-payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPaymentRequestBody("Valor errado", "PIX", "10.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelPayment_onOpenTab_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel-payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPaymentRequestBody("Motivo qualquer", "PIX", "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelPayment_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "0")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel-payment")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPaymentRequestBody("Motivo qualquer", "PIX", "0")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelPayment_withoutReason_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/pay")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payTabRequestBody("CASH", "0")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel-payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPaymentRequestBody(null, "PIX", "0")))
                .andExpect(status().isBadRequest());
    }
}
