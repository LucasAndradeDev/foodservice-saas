package com.example.restaurant_saas.controller;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.dto.request.LoginRequest;
import com.example.restaurant_saas.domain.entity.Restaurant;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequestRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.domain.enums.TableRequestType;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.repository.TableRequestRepository;
import com.example.restaurant_saas.support.TenantTestSupport;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.service.PublicTableRequestService;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TableRequestControllerIntegrationTest {

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

    @Autowired
    private PublicTableRequestService publicTableRequestService;

    @Autowired
    private TableRequestRepository tableRequestRepository;

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
    // approve it directly (Restaurant carries no tenant RLS/@Filter, see AdminRestaurantService)
    // then log in to get a working token, since registration itself no longer hands one out.
    private String registerOwnerAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String restaurantId = JsonPath.read(result.getResponse().getContentAsString(), "$.restaurant.id");
        Restaurant restaurant = restaurantRepository.findById(UUID.fromString(restaurantId)).orElseThrow();
        restaurant.setApproved(true);
        restaurantRepository.save(restaurant);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(registerRequest.getOwnerEmail());
        loginRequest.setPassword(registerRequest.getOwnerPassword());
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
    }

    private String getSlug(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
    }

    private String createTable(String token, int number) throws Exception {
        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(number);
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createProduct(String token) throws Exception {
        CreateCategoryRequest category = new CreateCategoryRequest();
        category.setName("Burgers");
        MvcResult categoryResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(category)))
                .andExpect(status().isCreated())
                .andReturn();
        String categoryId = JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.id");

        CreateProductRequest product = new CreateProductRequest();
        product.setName("Cheeseburger");
        product.setPrice(new BigDecimal("25.90"));
        product.setCategoryId(UUID.fromString(categoryId));
        MvcResult productResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(productResult.getResponse().getContentAsString(), "$.id");
    }

    private String placeOrder(String slug, String tableId, String productId) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        MvcResult result = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");
    }

    private void deliverItem(String token, String itemId) throws Exception {
        for (String status : new String[] {"PREPARING", "READY", "DELIVERED"}) {
            mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + status + "\"}"))
                    .andExpect(status().isOk());
        }
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

    @Test
    void createRequest_withoutAuthentication_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String tableId = createTable(ownerToken, 1);

        CreateTableRequestRequest request = new CreateTableRequestRequest();
        request.setType(TableRequestType.CALL_WAITER);

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableId").value(tableId))
                .andExpect(jsonPath("$.type").value("CALL_WAITER"))
                .andExpect(jsonPath("$.acknowledgedAt").doesNotExist());
    }

    @Test
    void createRequest_whenAlreadyPending_shouldReturnExistingInsteadOfDuplicating() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String tableId = createTable(ownerToken, 1);

        CreateTableRequestRequest request = new CreateTableRequestRequest();
        request.setType(TableRequestType.CALL_WAITER);

        MvcResult first = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId));

        String waiterToken = tokenFor(createUserDirectly(userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow(), UserRole.WAITER));
        mockMvc.perform(get("/api/v1/table-requests")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void listPending_asWaiter_shouldReturnUnacknowledgedRequests() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String tableId = createTable(ownerToken, 1);

        CreateTableRequestRequest request = new CreateTableRequestRequest();
        request.setType(TableRequestType.CALL_WAITER);
        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));

        mockMvc.perform(get("/api/v1/table-requests")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].tableId").value(tableId))
                .andExpect(jsonPath("$[0].type").value("CALL_WAITER"));
    }

    @Test
    void acknowledge_asWaiter_shouldRemoveFromPendingList() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String tableId = createTable(ownerToken, 1);

        CreateTableRequestRequest request = new CreateTableRequestRequest();
        request.setType(TableRequestType.CALL_WAITER);
        MvcResult created = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String requestId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        String waiterToken = tokenFor(createUserDirectly(owner, UserRole.WAITER));

        mockMvc.perform(patch("/api/v1/table-requests/" + requestId + "/acknowledge")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledgedAt", org.hamcrest.Matchers.notNullValue()));

        mockMvc.perform(get("/api/v1/table-requests")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        // A new call from the same table should create a fresh request, since the previous one was acknowledged.
        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", org.hamcrest.Matchers.not(requestId)));
    }

    @Test
    void createRequest_withTypeRequestBill_whenTableHasNoOrders_shouldFail() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String tableId = createTable(ownerToken, 1);

        CreateTableRequestRequest requestBill = new CreateTableRequestRequest();
        requestBill.setType(TableRequestType.REQUEST_BILL);
        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBill)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRequest_withTypeRequestBill_whenOrderStillInKitchen_shouldFail() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String tableId = createTable(ownerToken, 1);
        String productId = createProduct(ownerToken);
        placeOrder(slug, tableId, productId);

        CreateTableRequestRequest requestBill = new CreateTableRequestRequest();
        requestBill.setType(TableRequestType.REQUEST_BILL);
        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBill)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRequest_forDifferentTypesOnSameTable_shouldCreateBothAsSeparatePendingRequests() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String tableId = createTable(ownerToken, 1);
        String productId = createProduct(ownerToken);
        String itemId = placeOrder(slug, tableId, productId);
        deliverItem(ownerToken, itemId);

        CreateTableRequestRequest callWaiter = new CreateTableRequestRequest();
        callWaiter.setType(TableRequestType.CALL_WAITER);
        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callWaiter)))
                .andExpect(status().isCreated());

        CreateTableRequestRequest requestBill = new CreateTableRequestRequest();
        requestBill.setType(TableRequestType.REQUEST_BILL);
        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBill)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REQUEST_BILL"));

        String waiterToken = tokenFor(createUserDirectly(userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow(), UserRole.WAITER));
        mockMvc.perform(get("/api/v1/table-requests")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    // Regression test for a real bug found testing this session: the read-then-insert in
    // PublicTableRequestService#createRequest isn't atomic, so several truly concurrent
    // double-clicks/retries on "chamar garçom" can all pass the same null-pending check before any
    // of them commits. idx_table_requests_pending_unique (V76) makes the database the tiebreaker,
    // but the very first version of the fix - a plain try/catch around save() inside the caller's
    // own transaction - turned every losing thread into an unhandled 500 instead of the intended
    // graceful fallback: Postgres aborts the *whole* transaction on a constraint violation, so the
    // fallback read that same catch block tried next ran on that now-poisoned connection and threw
    // too. TableRequestInsertService#insert's own REQUIRES_NEW transaction (a separate pooled
    // connection) is what actually fixes it - confirmed live under 15-way real concurrency before
    // this test was written.
    @Test
    void createRequest_underRealConcurrency_shouldNeverDuplicateOrThrow() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String tableId = createTable(ownerToken, 1);

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        ConcurrentLinkedQueue<UUID> resultIds = new ConcurrentLinkedQueue<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        go.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    resultIds.add(TenantTestSupport.withTenant(
                            restaurantIdFor(registerRequest.getOwnerEmail()),
                            () -> publicTableRequestService.createRequest(slug, UUID.fromString(tableId), TableRequestType.CALL_WAITER).getId()));
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            // Propagates any exception a thread threw - the whole point of this test is that none should.
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        Set<UUID> distinctIds = resultIds.stream().collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(resultIds).hasSize(threadCount);
        org.assertj.core.api.Assertions.assertThat(distinctIds).hasSize(1);

        long rowCount = TenantTestSupport.withTenant(restaurantIdFor(registerRequest.getOwnerEmail()),
                () -> tableRequestRepository.findByTableIdAndTypeAndAcknowledgedAtIsNull(UUID.fromString(tableId), TableRequestType.CALL_WAITER))
                .stream().count();
        org.assertj.core.api.Assertions.assertThat(rowCount).isEqualTo(1);
    }

    private UUID restaurantIdFor(String email) {
        return userRepository.findByEmailBypassingRls(email).orElseThrow().getRestaurant().getId();
    }

    @Test
    void listPending_asKitchen_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        String kitchenToken = tokenFor(createUserDirectly(owner, UserRole.KITCHEN));

        mockMvc.perform(get("/api/v1/table-requests")
                        .header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isForbidden());
    }
}
