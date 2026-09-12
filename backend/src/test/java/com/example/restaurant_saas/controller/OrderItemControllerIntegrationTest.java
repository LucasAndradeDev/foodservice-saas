package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.ApplyDiscountRequest;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.LoginRequest;
import com.example.restaurant_saas.dto.request.OpenCashRegisterRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PaymentEntryRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.TransferItemsRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.support.TenantTestSupport;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private OrderItemRepository orderItemRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RestaurantRepository restaurantRepository;

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

    // A new signup is unapproved by default (AuthService#registerRestaurant) and can't log in -
    // approve it directly then log in to get a working token, since registration itself no
    // longer hands one out.
    private String registerOwnerAndGetToken() throws Exception {
        String token = registerAndGetToken(registerRequest);
        // A test pays a tab in CASH; opening a session here keeps it green now that CASH
        // payments require an open cash register.
        openCashRegister(token);
        return token;
    }

    private void openCashRegister(String token) throws Exception {
        OpenCashRegisterRequest request = new OpenCashRegisterRequest();
        request.setOpeningAmount(new BigDecimal("100.00"));
        mockMvc.perform(post("/api/v1/cash-register/open")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
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

    private User createUserDirectly(User owner, UserRole role) {
        User user = User.builder()
                .restaurant(owner.getRestaurant())
                .name(role.name())
                .email(role.name().toLowerCase() + "+" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(role)
                .active(true)
                .build();
        return TenantTestSupport.withTenant(owner.getRestaurant().getId(), () -> userRepository.save(user));
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

    private String discountRequestBody(DiscountType type, String value, String reason) throws Exception {
        ApplyDiscountRequest request = new ApplyDiscountRequest();
        request.setDiscountType(type);
        if (value != null) {
            request.setDiscountValue(new BigDecimal(value));
        }
        request.setReason(reason);
        return objectMapper.writeValueAsString(request);
    }

    private String transferRequestBody(List<String> itemIds, String targetTabId) throws Exception {
        TransferItemsRequest request = new TransferItemsRequest();
        request.setItemIds(itemIds.stream().map(UUID::fromString).toList());
        request.setTargetTabId(UUID.fromString(targetTabId));
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
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
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
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
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
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
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
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
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
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
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
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("CANCELLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("WAITER"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
    }

    @Test
    void updateStatus_cancelFromReady_asKitchen_shouldSucceed() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
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
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("KITCHEN"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
    }

    // Regression test for the status-transition race fixed in this session (found during code
    // review): OrderItemService#updateStatus used to read the item with a plain find, so two
    // concurrent status changes on the same READY item - a waiter serving it (READY->DELIVERED)
    // racing a kitchen cancellation (READY->CANCELLED) - could both read from=READY and both pass
    // isValidTransition/rolesAllowedFor before either committed, letting whichever write landed
    // last silently overwrite the other with no error. DELIVERED and CANCELLED are both terminal
    // (isValidTransition returns false for any transition out of either), so with the fix in place
    // exactly one of the two calls can ever succeed regardless of scheduling order - the loser
    // re-reads the real post-lock status and correctly gets rejected instead of also applying.
    @Test
    void updateStatus_concurrentConflictingTransitionsFromReady_shouldOnlyApplyOne() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String kitchenToken = tokenFor(createUserDirectly(owner, UserRole.KITCHEN));
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

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> raceStatusUpdate(waiterToken, itemId, "DELIVERED", ready, go, successes, failures)));
            futures.add(executor.submit(() -> raceStatusUpdate(kitchenToken, itemId, "CANCELLED", ready, go, successes, failures)));
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        assertEquals(1, successes.get(), "exactly one of the two concurrent transitions should succeed");
        assertEquals(1, failures.get(), "the other must be rejected instead of silently overwriting the winner");

        UUID restaurantId = owner.getRestaurant().getId();
        ItemStatus finalStatus = TenantTestSupport.withTenant(restaurantId, () -> orderItemRepository.findById(UUID.fromString(itemId)))
                .orElseThrow().getStatus();
        assertTrue(finalStatus == ItemStatus.DELIVERED || finalStatus == ItemStatus.CANCELLED,
                "final status must be a real outcome of exactly one transition, not something else");
    }

    private void raceStatusUpdate(
            String token, String itemId, String targetStatus,
            CountDownLatch ready, CountDownLatch go, AtomicInteger successes, AtomicInteger failures
    ) {
        ready.countDown();
        try {
            go.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            MvcResult result = mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateStatusRequestBody(targetStatus)))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                successes.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
        } catch (Exception e) {
            failures.incrementAndGet();
        }
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

    @Test
    void applyDiscount_fixedAmount_shouldReduceNetSubtotal() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/discount")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.FIXED, "5.00", "Prato atrasou")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountType").value("FIXED"))
                .andExpect(jsonPath("$.discountAmount").value(5.00))
                .andExpect(jsonPath("$.discountReason").value("Prato atrasou"))
                .andExpect(jsonPath("$.discountAppliedBy").value("Owner"))
                .andExpect(jsonPath("$.netSubtotal").value(20.90));
    }

    @Test
    void applyDiscount_percentage_shouldReduceNetSubtotalProportionally() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/discount")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.PERCENTAGE, "10", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAmount").value(2.59))
                .andExpect(jsonPath("$.netSubtotal").value(23.31));
    }

    @Test
    void applyDiscount_withNullType_shouldClearExistingDiscount() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/discount")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.FIXED, "5.00", "Reclamação")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/discount")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountType").doesNotExist())
                .andExpect(jsonPath("$.discountReason").doesNotExist())
                .andExpect(jsonPath("$.netSubtotal").value(25.90));
    }

    @Test
    void applyDiscount_asWaiter_shouldBeForbidden() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/discount")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.FIXED, "5.00", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void applyDiscount_percentageOver100_shouldReturn400() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/discount")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.PERCENTAGE, "150", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyDiscount_fixedAboveSubtotal_shouldReturn400() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/discount")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.FIXED, "999.00", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyDiscount_onCancelledItem_shouldReturn400() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("CANCELLED")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/discount")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.FIXED, "5.00", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferItems_singleItem_shouldMoveToNewOrderInTargetTabAndKeepSourceOrder() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());
        String otherTableId = createTableAndGetId(setup.ownerToken());
        String targetTabId = openTabAndGetId(setup.ownerToken(), otherTableId);

        mockMvc.perform(post("/api/v1/order-items/transfer")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestBody(List.of(itemId), targetTabId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(itemId));

        // Item now shows up under a new order on the target tab, with its data intact.
        mockMvc.perform(get("/api/v1/tabs/" + targetTabId + "/orders")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].items.length()").value(1))
                .andExpect(jsonPath("$[0].items[0].id").value(itemId))
                .andExpect(jsonPath("$[0].items[0].status").value("PENDING"));

        // The source order is never deleted, just left empty (item wasn't orphan-removed by Hibernate).
        mockMvc.perform(get("/api/v1/tabs/" + setup.tabId() + "/orders")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].items.length()").value(0));
    }

    @Test
    void transferItems_multipleItems_shouldGroupIntoOneNewOrder() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId1 = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());
        String itemId2 = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());
        String otherTableId = createTableAndGetId(setup.ownerToken());
        String targetTabId = openTabAndGetId(setup.ownerToken(), otherTableId);

        mockMvc.perform(post("/api/v1/order-items/transfer")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestBody(List.of(itemId1, itemId2), targetTabId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/tabs/" + targetTabId + "/orders")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].items.length()").value(2));
    }

    @Test
    void transferItems_preservesDiscountAndDeliveredStatus() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/discount")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountRequestBody(DiscountType.FIXED, "5.00", "Cortesia")))
                .andExpect(status().isOk());
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

        String otherTableId = createTableAndGetId(setup.ownerToken());
        String targetTabId = openTabAndGetId(setup.ownerToken(), otherTableId);

        mockMvc.perform(post("/api/v1/order-items/transfer")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestBody(List.of(itemId), targetTabId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$[0].discountType").value("FIXED"))
                .andExpect(jsonPath("$[0].netSubtotal").value(20.90));
    }

    @Test
    void transferItems_cancelledItem_shouldReturn400() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusRequestBody("CANCELLED")))
                .andExpect(status().isOk());

        String otherTableId = createTableAndGetId(setup.ownerToken());
        String targetTabId = openTabAndGetId(setup.ownerToken(), otherTableId);

        mockMvc.perform(post("/api/v1/order-items/transfer")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestBody(List.of(itemId), targetTabId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferItems_toSameTab_shouldReturn400() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        mockMvc.perform(post("/api/v1/order-items/transfer")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestBody(List.of(itemId), setup.tabId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferItems_itemsFromDifferentTabs_shouldReturn400() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId1 = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        String otherTableId = createTableAndGetId(setup.ownerToken());
        String otherTabId = openTabAndGetId(setup.ownerToken(), otherTableId);
        String itemId2 = createOrderAndGetFirstItemId(setup.ownerToken(), otherTabId, setup.productId());

        String targetTableId = createTableAndGetId(setup.ownerToken());
        String targetTabId = openTabAndGetId(setup.ownerToken(), targetTableId);

        mockMvc.perform(post("/api/v1/order-items/transfer")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestBody(List.of(itemId1, itemId2), targetTabId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferItems_sourceTabNotOpen_shouldReturn400() throws Exception {
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

        PaymentEntryRequest entry = new PaymentEntryRequest();
        entry.setPaymentMethod(PaymentMethod.CASH);
        entry.setAmount(new BigDecimal("25.90"));
        RegisterPaymentsRequest payRequest = new RegisterPaymentsRequest();
        payRequest.setPayments(List.of(entry));
        payRequest.setServiceChargePercentage(BigDecimal.ZERO);
        mockMvc.perform(post("/api/v1/tabs/" + setup.tabId() + "/payments")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payRequest)))
                .andExpect(status().isOk());

        String otherTableId = createTableAndGetId(setup.ownerToken());
        String targetTabId = openTabAndGetId(setup.ownerToken(), otherTableId);

        mockMvc.perform(post("/api/v1/order-items/transfer")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestBody(List.of(itemId), targetTabId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferItems_targetTabNotOpen_shouldReturn400() throws Exception {
        TestSetup setup = setupTabWithProduct();
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        String otherTableId = createTableAndGetId(setup.ownerToken());
        String targetTabId = openTabAndGetId(setup.ownerToken(), otherTableId);
        mockMvc.perform(patch("/api/v1/tabs/" + targetTabId + "/cancel")
                        .header("Authorization", "Bearer " + setup.ownerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/order-items/transfer")
                        .header("Authorization", "Bearer " + setup.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestBody(List.of(itemId), targetTabId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferItems_asKitchen_shouldBeForbidden() throws Exception {
        TestSetup setup = setupTabWithProduct();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        String kitchenToken = tokenFor(createUserDirectly(owner, UserRole.KITCHEN));
        String itemId = createOrderAndGetFirstItemId(setup.ownerToken(), setup.tabId(), setup.productId());

        String otherTableId = createTableAndGetId(setup.ownerToken());
        String targetTabId = openTabAndGetId(setup.ownerToken(), otherTableId);

        mockMvc.perform(post("/api/v1/order-items/transfer")
                        .header("Authorization", "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestBody(List.of(itemId), targetTabId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void transferItems_nonexistentItem_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String tableId = createTableAndGetId(ownerToken);
        String targetTabId = openTabAndGetId(ownerToken, tableId);

        mockMvc.perform(post("/api/v1/order-items/transfer")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestBody(List.of(UUID.randomUUID().toString()), targetTabId)))
                .andExpect(status().isBadRequest());
    }
}
