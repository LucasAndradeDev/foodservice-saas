package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequestRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.domain.enums.TableRequestType;
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

        String waiterToken = tokenFor(createUserDirectly(userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow(), UserRole.WAITER));
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

        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
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

        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
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
    void createRequest_forDifferentTypesOnSameTable_shouldCreateBothAsSeparatePendingRequests() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String slug = getSlug(ownerToken);
        String tableId = createTable(ownerToken, 1);

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

        String waiterToken = tokenFor(createUserDirectly(userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow(), UserRole.WAITER));
        mockMvc.perform(get("/api/v1/table-requests")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void listPending_asKitchen_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        String kitchenToken = tokenFor(createUserDirectly(owner, UserRole.KITCHEN));

        mockMvc.perform(get("/api/v1/table-requests")
                        .header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isForbidden());
    }
}
