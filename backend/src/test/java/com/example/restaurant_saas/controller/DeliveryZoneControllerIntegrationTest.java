package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateDeliveryZoneRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateDeliveryZoneRequest;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.UserDetailsImpl;
import com.example.restaurant_saas.support.TenantTestSupport;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DeliveryZoneControllerIntegrationTest {

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
        return TenantTestSupport.withTenant(owner.getRestaurant().getId(), () -> userRepository.save(user));
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(new UserDetailsImpl(user));
    }

    private String createZone(String token, String neighborhood, String fee) throws Exception {
        CreateDeliveryZoneRequest request = new CreateDeliveryZoneRequest();
        request.setNeighborhood(neighborhood);
        request.setFee(new BigDecimal(fee));
        MvcResult result = mockMvc.perform(post("/api/v1/delivery-zones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void createZone_asOwner_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateDeliveryZoneRequest request = new CreateDeliveryZoneRequest();
        request.setNeighborhood("Centro");
        request.setFee(new BigDecimal("8.00"));

        mockMvc.perform(post("/api/v1/delivery-zones")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.neighborhood").value("Centro"))
                .andExpect(jsonPath("$.fee").value(8.00))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createZone_withDuplicateNeighborhoodCaseInsensitive_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        createZone(ownerToken, "Centro", "8.00");

        CreateDeliveryZoneRequest duplicateRequest = new CreateDeliveryZoneRequest();
        duplicateRequest.setNeighborhood("CENTRO");
        duplicateRequest.setFee(new BigDecimal("9.00"));

        mockMvc.perform(post("/api/v1/delivery-zones")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createZone_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateDeliveryZoneRequest request = new CreateDeliveryZoneRequest();
        request.setNeighborhood("Centro");
        request.setFee(new BigDecimal("8.00"));

        mockMvc.perform(post("/api/v1/delivery-zones")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listZones_asWaiter_shouldSucceedOrderedByNeighborhood() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        createZone(ownerToken, "Vila Nova", "10.00");
        createZone(ownerToken, "Centro", "8.00");

        mockMvc.perform(get("/api/v1/delivery-zones")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].neighborhood").value("Centro"))
                .andExpect(jsonPath("$[1].neighborhood").value("Vila Nova"));
    }

    @Test
    void updateZone_asOwner_shouldChangeFeeAndDeactivate() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String zoneId = createZone(ownerToken, "Centro", "8.00");

        UpdateDeliveryZoneRequest updateRequest = new UpdateDeliveryZoneRequest();
        updateRequest.setNeighborhood("Centro");
        updateRequest.setFee(new BigDecimal("12.50"));
        updateRequest.setActive(false);

        mockMvc.perform(put("/api/v1/delivery-zones/" + zoneId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(12.50))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteZone_asOwner_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String zoneId = createZone(ownerToken, "Centro", "8.00");

        mockMvc.perform(delete("/api/v1/delivery-zones/" + zoneId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/delivery-zones")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updateZone_crossTenant_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String zoneId = createZone(ownerToken, "Centro", "8.00");

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        MvcResult otherResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherRestaurant)))
                .andExpect(status().isCreated())
                .andReturn();
        String otherToken = JsonPath.read(otherResult.getResponse().getContentAsString(), "$.accessToken");

        UpdateDeliveryZoneRequest updateRequest = new UpdateDeliveryZoneRequest();
        updateRequest.setNeighborhood("Centro");
        updateRequest.setFee(new BigDecimal("1.00"));
        updateRequest.setActive(true);

        mockMvc.perform(put("/api/v1/delivery-zones/" + zoneId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }
}
