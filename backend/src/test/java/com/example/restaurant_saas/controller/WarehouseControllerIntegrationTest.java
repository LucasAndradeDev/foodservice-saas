package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.repository.WarehouseIntegrationRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WarehouseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WarehouseIntegrationRepository warehouseIntegrationRepository;

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

    @Test
    void handoff_asOwner_returnsHandoffUrlAndStoresHashedApiKey() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        UUID restaurantId = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail())
                .orElseThrow().getRestaurant().getId();

        MvcResult result = mockMvc.perform(post("/api/v1/warehouse/handoff")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handoffUrl").isNotEmpty())
                .andReturn();

        String handoffUrl = JsonPath.read(result.getResponse().getContentAsString(), "$.handoffUrl");
        assertTrue(handoffUrl.contains("/sso?token="), "handoff URL should carry the token as a query param");

        var integration = TenantTestSupport.withTenant(restaurantId,
                () -> warehouseIntegrationRepository.findByRestaurantId(restaurantId));
        assertTrue(integration.isPresent());
        // Only the hash is stored server-side - never the raw key embedded in the handoff URL.
        String token = handoffUrl.substring(handoffUrl.indexOf("token=") + "token=".length());
        assertFalse(token.contains(integration.get().getApiKeyHash()));
    }

    @Test
    void handoff_calledTwice_rotatesStoredHashInsteadOfCreatingSecondRow() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        UUID restaurantId = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail())
                .orElseThrow().getRestaurant().getId();

        mockMvc.perform(post("/api/v1/warehouse/handoff").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        String firstHash = TenantTestSupport.withTenant(restaurantId,
                        () -> warehouseIntegrationRepository.findByRestaurantId(restaurantId))
                .orElseThrow().getApiKeyHash();

        mockMvc.perform(post("/api/v1/warehouse/handoff").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        String secondHash = TenantTestSupport.withTenant(restaurantId,
                        () -> warehouseIntegrationRepository.findByRestaurantId(restaurantId))
                .orElseThrow().getApiKeyHash();

        // Unique constraint on restaurant_id means a second row would have failed outright -
        // reaching this point already proves the second handoff updated the same row.
        assertNotEquals(firstHash, secondHash);
    }

    @Test
    void handoff_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        mockMvc.perform(post("/api/v1/warehouse/handoff").header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void handoff_withoutToken_shouldBeForbidden() throws Exception {
        // No AuthenticationEntryPoint is configured (same as every other endpoint in this app),
        // so an anonymous request fails the role check with 403, not 401.
        mockMvc.perform(post("/api/v1/warehouse/handoff"))
                .andExpect(status().isForbidden());
    }
}
