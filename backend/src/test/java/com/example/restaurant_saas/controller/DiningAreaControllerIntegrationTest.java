package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateDiningAreaRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.ReorderDiningAreasRequest;
import com.example.restaurant_saas.dto.request.UpdateDiningAreaRequest;
import com.example.restaurant_saas.dto.request.UpdateTableRequest;
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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DiningAreaControllerIntegrationTest {

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
        return TenantTestSupport.withTenant(owner.getRestaurant().getId(), () -> userRepository.save(user));
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(new UserDetailsImpl(user));
    }

    private String createArea(String token, String name) throws Exception {
        CreateDiningAreaRequest request = new CreateDiningAreaRequest();
        request.setName(name);
        MvcResult result = mockMvc.perform(post("/api/v1/dining-areas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void createArea_asOwner_shouldSucceedAtEndOfOrder() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        createArea(ownerToken, "Salão interno");

        CreateDiningAreaRequest request = new CreateDiningAreaRequest();
        request.setName("Varanda");
        mockMvc.perform(post("/api/v1/dining-areas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Varanda"))
                .andExpect(jsonPath("$.displayOrder").value(1));
    }

    @Test
    void createArea_withDuplicateNameCaseInsensitive_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createArea(ownerToken, "Varanda");

        CreateDiningAreaRequest duplicateRequest = new CreateDiningAreaRequest();
        duplicateRequest.setName("VARANDA");

        mockMvc.perform(post("/api/v1/dining-areas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createArea_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateDiningAreaRequest request = new CreateDiningAreaRequest();
        request.setName("Balcão");

        mockMvc.perform(post("/api/v1/dining-areas")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAreas_asWaiter_shouldSucceedOrderedByDisplayOrder() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        createArea(ownerToken, "Salão interno");
        createArea(ownerToken, "Varanda");

        mockMvc.perform(get("/api/v1/dining-areas")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Salão interno"))
                .andExpect(jsonPath("$[1].name").value("Varanda"));
    }

    @Test
    void updateArea_asOwner_shouldRename() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String areaId = createArea(ownerToken, "Salão interno");

        UpdateDiningAreaRequest updateRequest = new UpdateDiningAreaRequest();
        updateRequest.setName("Salão principal");

        mockMvc.perform(put("/api/v1/dining-areas/" + areaId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Salão principal"));
    }

    @Test
    void updateArea_renameToExistingName_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createArea(ownerToken, "Salão interno");
        String varandaId = createArea(ownerToken, "Varanda");

        UpdateDiningAreaRequest renameRequest = new UpdateDiningAreaRequest();
        renameRequest.setName("Salão interno");

        mockMvc.perform(put("/api/v1/dining-areas/" + varandaId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(renameRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteArea_withTableAssigned_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String areaId = createArea(ownerToken, "Salão interno");

        CreateTableRequest tableRequest = new CreateTableRequest();
        tableRequest.setAreaId(UUID.fromString(areaId));
        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tableRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/dining-areas/" + areaId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteArea_withoutTablesAssigned_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String areaId = createArea(ownerToken, "Salão interno");

        mockMvc.perform(delete("/api/v1/dining-areas/" + areaId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteArea_afterTableUnassigned_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String areaId = createArea(ownerToken, "Salão interno");

        CreateTableRequest tableRequest = new CreateTableRequest();
        tableRequest.setAreaId(UUID.fromString(areaId));
        MvcResult tableResult = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tableRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String tableId = JsonPath.read(tableResult.getResponse().getContentAsString(), "$.id");

        UpdateTableRequest clearRequest = new UpdateTableRequest();
        clearRequest.setClearArea(true);
        mockMvc.perform(put("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clearRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").doesNotExist());

        mockMvc.perform(delete("/api/v1/dining-areas/" + areaId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void reorderAreas_asOwner_shouldUpdateDisplayOrder() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String firstId = createArea(ownerToken, "Salão interno");
        String secondId = createArea(ownerToken, "Varanda");

        ReorderDiningAreasRequest reorderRequest = new ReorderDiningAreasRequest();
        reorderRequest.setOrderedIds(List.of(UUID.fromString(secondId), UUID.fromString(firstId)));

        mockMvc.perform(put("/api/v1/dining-areas/reorder")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reorderRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Varanda"))
                .andExpect(jsonPath("$[1].name").value("Salão interno"));
    }

    @Test
    void reorderAreas_withMismatchedIds_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createArea(ownerToken, "Salão interno");

        ReorderDiningAreasRequest reorderRequest = new ReorderDiningAreasRequest();
        reorderRequest.setOrderedIds(List.of(UUID.randomUUID()));

        mockMvc.perform(put("/api/v1/dining-areas/reorder")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reorderRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getArea_crossTenant_cannotBeAssignedToTable() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        String otherToken = registerAndGetToken(otherRestaurant);
        String otherAreaId = createArea(otherToken, "Salão interno");

        CreateTableRequest tableRequest = new CreateTableRequest();
        tableRequest.setAreaId(UUID.fromString(otherAreaId));

        mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tableRequest)))
                .andExpect(status().isBadRequest());
    }
}
