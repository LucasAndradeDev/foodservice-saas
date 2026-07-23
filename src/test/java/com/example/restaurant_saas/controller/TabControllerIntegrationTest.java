package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
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
    void closeTab_shouldFreeAllLinkedTables() throws Exception {
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

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/close")
                        .header("Authorization", "Bearer " + ownerToken))
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
    void closeTab_alreadyClosed_shouldReturn400() throws Exception {
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

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/close")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/close")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closeTab_asKitchen_shouldBeForbidden() throws Exception {
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

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/close")
                        .header("Authorization", "Bearer " + kitchenToken))
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

        mockMvc.perform(patch("/api/v1/tabs/" + firstTabId + "/close")
                        .header("Authorization", "Bearer " + ownerToken))
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
}
