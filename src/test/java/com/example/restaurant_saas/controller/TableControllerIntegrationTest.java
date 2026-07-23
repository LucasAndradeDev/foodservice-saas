package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.CreateTablesBulkRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateTableRequest;
import com.example.restaurant_saas.dto.request.UpdateTableStatusRequest;
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
class TableControllerIntegrationTest {

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

    @Test
    void createTable_asOwner_shouldSucceedWithFreeStatus() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(1);

        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("FREE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createTable_withDuplicateNumber_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(1);

        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTable_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(1);

        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTable_asManager_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User manager = createUserDirectly(owner, UserRole.MANAGER);
        String managerToken = tokenFor(manager);

        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(1);

        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void createTable_withoutNumber_shouldAutoAssignNextNumber() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateTableRequest request = new CreateTableRequest();

        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    void createTable_withoutNumber_afterManualNumber_shouldContinueFromHighest() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateTableRequest manualRequest = new CreateTableRequest();
        manualRequest.setNumber(10);
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manualRequest)))
                .andExpect(status().isCreated());

        CreateTableRequest autoRequest = new CreateTableRequest();
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(autoRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(11));
    }

    @Test
    void createTablesBulk_asOwner_shouldCreateSequentialNumbers() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateTablesBulkRequest request = new CreateTablesBulkRequest();
        request.setQuantity(5);

        mockMvc.perform(post("/api/v1/tables/bulk")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].status").value("FREE"))
                .andExpect(jsonPath("$[4].number").value(5));
    }

    @Test
    void createTablesBulk_continuesFromHighestExistingNumber() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateTableRequest existingRequest = new CreateTableRequest();
        existingRequest.setNumber(10);
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingRequest)))
                .andExpect(status().isCreated());

        CreateTablesBulkRequest bulkRequest = new CreateTablesBulkRequest();
        bulkRequest.setQuantity(3);

        mockMvc.perform(post("/api/v1/tables/bulk")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].number").value(11))
                .andExpect(jsonPath("$[2].number").value(13));
    }

    @Test
    void createTablesBulk_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateTablesBulkRequest request = new CreateTablesBulkRequest();
        request.setQuantity(5);

        mockMvc.perform(post("/api/v1/tables/bulk")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTablesBulk_withInvalidQuantity_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateTablesBulkRequest request = new CreateTablesBulkRequest();
        request.setQuantity(0);

        mockMvc.perform(post("/api/v1/tables/bulk")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listTables_asWaiter_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(1);
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tables")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listTables_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/tables"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listTables_filterByStatus_shouldReturnOnlyMatching() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateTableRequest firstRequest = new CreateTableRequest();
        firstRequest.setNumber(1);
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        CreateTableRequest secondRequest = new CreateTableRequest();
        secondRequest.setNumber(2);
        MvcResult secondResult = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String secondTableId = JsonPath.read(secondResult.getResponse().getContentAsString(), "$.id");

        UpdateTableStatusRequest occupyRequest = new UpdateTableStatusRequest();
        occupyRequest.setStatus(com.example.restaurant_saas.domain.enums.TableStatus.OCCUPIED);
        mockMvc.perform(patch("/api/v1/tables/" + secondTableId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(occupyRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tables").param("status", "FREE")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value(1));
    }

    @Test
    void getTable_crossTenant_shouldNotBeFound() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        String otherToken = registerAndGetToken(otherRestaurant);

        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(1);
        MvcResult createResult = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String otherTableId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/tables/" + otherTableId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTable_asOwner_shouldChangeNumberAndActive() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(1);
        MvcResult createResult = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String tableId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateTableRequest updateRequest = new UpdateTableRequest();
        updateRequest.setNumber(2);
        updateRequest.setActive(false);

        mockMvc.perform(put("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateTable_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(1);
        MvcResult createResult = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String tableId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateTableRequest updateRequest = new UpdateTableRequest();
        updateRequest.setNumber(2);

        mockMvc.perform(put("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateTable_renameToExistingNumber_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateTableRequest firstRequest = new CreateTableRequest();
        firstRequest.setNumber(1);
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        CreateTableRequest secondRequest = new CreateTableRequest();
        secondRequest.setNumber(2);
        MvcResult secondResult = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String secondTableId = JsonPath.read(secondResult.getResponse().getContentAsString(), "$.id");

        UpdateTableRequest renameRequest = new UpdateTableRequest();
        renameRequest.setNumber(1);

        mockMvc.perform(put("/api/v1/tables/" + secondTableId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(renameRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTableStatus_asWaiter_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(1);
        MvcResult createResult = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String tableId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateTableStatusRequest statusRequest = new UpdateTableStatusRequest();
        statusRequest.setStatus(com.example.restaurant_saas.domain.enums.TableStatus.OCCUPIED);

        mockMvc.perform(patch("/api/v1/tables/" + tableId + "/status")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
    }

    @Test
    void updateTableStatus_fullCycle_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(1);
        MvcResult createResult = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String tableId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateTableStatusRequest occupyRequest = new UpdateTableStatusRequest();
        occupyRequest.setStatus(com.example.restaurant_saas.domain.enums.TableStatus.OCCUPIED);
        mockMvc.perform(patch("/api/v1/tables/" + tableId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(occupyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));

        UpdateTableStatusRequest closingRequest = new UpdateTableStatusRequest();
        closingRequest.setStatus(com.example.restaurant_saas.domain.enums.TableStatus.CLOSING);
        mockMvc.perform(patch("/api/v1/tables/" + tableId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closingRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSING"));

        UpdateTableStatusRequest freeRequest = new UpdateTableStatusRequest();
        freeRequest.setStatus(com.example.restaurant_saas.domain.enums.TableStatus.FREE);
        mockMvc.perform(patch("/api/v1/tables/" + tableId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    void updateTableStatus_crossTenant_shouldNotBeFound() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another2+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        String otherToken = registerAndGetToken(otherRestaurant);

        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(1);
        MvcResult createResult = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String otherTableId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateTableStatusRequest statusRequest = new UpdateTableStatusRequest();
        statusRequest.setStatus(com.example.restaurant_saas.domain.enums.TableStatus.OCCUPIED);

        mockMvc.perform(patch("/api/v1/tables/" + otherTableId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isBadRequest());
    }
}
