package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.AdminCredentials;
import com.example.restaurant_saas.domain.entity.AdminPasswordResetToken;
import com.example.restaurant_saas.dto.request.LoginRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.ResetPasswordRequest;
import com.example.restaurant_saas.repository.AdminCredentialsRepository;
import com.example.restaurant_saas.repository.AdminPasswordResetTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerIntegrationTest {

    private static final String ADMIN_USERNAME = "test-admin";
    private static final String ADMIN_PASSWORD = "admin-test-password-123";
    private static final String ADMIN_EMAIL = "admin-test@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminPasswordResetTokenRepository adminPasswordResetTokenRepository;

    @Autowired
    private AdminCredentialsRepository adminCredentialsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RegisterRestaurantRequest registerRequest;

    // admin_credentials is a singleton row shared with whatever's actually running against this
    // dev DB (see project_testcontainers_docker_desktop_incompatibility) - a developer manually
    // testing the real admin panel locally leaves this row with their own username/password,
    // which this class's hardcoded ADMIN_USERNAME/ADMIN_PASSWORD would then never match. Forcing
    // known credentials before each test (and restoring whatever was there before afterwards)
    // makes these tests deterministic regardless of that history, without clobbering a
    // developer's real local admin login.
    private AdminCredentials originalAdminCredentials;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Admin Test Restaurant");
        registerRequest.setPhone("11999999999");
        registerRequest.setAddress("Main St, 100");
        registerRequest.setOwnerName("Owner");
        registerRequest.setOwnerEmail("owner+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");

        AdminCredentials credentials = adminCredentialsRepository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(() -> AdminCredentials.builder().build());
        originalAdminCredentials = AdminCredentials.builder()
                .id(credentials.getId())
                .username(credentials.getUsername())
                .passwordHash(credentials.getPasswordHash())
                .email(credentials.getEmail())
                .build();

        credentials.setUsername(ADMIN_USERNAME);
        credentials.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        credentials.setEmail(ADMIN_EMAIL);
        adminCredentialsRepository.save(credentials);
    }

    @AfterEach
    void restoreAdminCredentials() {
        if (originalAdminCredentials.getId() == null) {
            // No row existed before this test forced one into place - leave the DB as it was.
            adminCredentialsRepository.findFirstByOrderByCreatedAtAsc().ifPresent(adminCredentialsRepository::delete);
            return;
        }
        AdminCredentials credentials = adminCredentialsRepository.findById(originalAdminCredentials.getId()).orElseThrow();
        credentials.setUsername(originalAdminCredentials.getUsername());
        credentials.setPasswordHash(originalAdminCredentials.getPasswordHash());
        credentials.setEmail(originalAdminCredentials.getEmail());
        adminCredentialsRepository.save(credentials);
    }

    private String registerRestaurantAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String adminLoginAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void adminLogin_withCorrectCredentials_shouldReturnToken() throws Exception {
        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void adminLogin_withWrongPassword_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminToken_cannotReachTenantEndpoint() throws Exception {
        String adminToken = adminLoginAndGetToken();

        mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantToken_cannotReachAdminEndpoint() throws Exception {
        String tenantToken = registerRestaurantAndGetToken();

        mockMvc.perform(get("/api/v1/admin/restaurants")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminToken_listsRestaurantsAcrossTenants() throws Exception {
        registerRequest.setOwnerEmail("ownerA+" + System.nanoTime() + "@test.com");
        registerRequest.setRestaurantName("Restaurant A " + System.nanoTime());
        registerRestaurantAndGetToken();

        registerRequest.setOwnerEmail("ownerB+" + System.nanoTime() + "@test.com");
        registerRequest.setRestaurantName("Restaurant B " + System.nanoTime());
        registerRestaurantAndGetToken();

        String adminToken = adminLoginAndGetToken();

        mockMvc.perform(get("/api/v1/admin/restaurants")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(2))));
    }

    @Test
    void adminBlocksRestaurant_thenOwnerLoginIsRejected_thenUnblockRestoresAccess() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String restaurantId = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.restaurant.id");

        String adminToken = adminLoginAndGetToken();

        mockMvc.perform(patch("/api/v1/admin/restaurants/" + restaurantId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false,\"paymentDueDate\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(registerRequest.getOwnerEmail());
        loginRequest.setPassword(registerRequest.getOwnerPassword());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Restaurant Suspended"));

        mockMvc.perform(patch("/api/v1/admin/restaurants/" + restaurantId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"paymentDueDate\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void adminForgotPassword_thenResetPassword_shouldAllowLoginWithNewPassword() throws Exception {
        mockMvc.perform(post("/api/v1/admin/forgot-password"))
                .andExpect(status().isNoContent());

        AdminPasswordResetToken resetToken = adminPasswordResetTokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow();

        ResetPasswordRequest resetRequest = new ResetPasswordRequest();
        resetRequest.setToken(resetToken.getToken());
        resetRequest.setNewPassword("brandNewAdminPassword789");

        // No try/finally needed here - restoreAdminCredentials() (@AfterEach) puts the original
        // row back regardless of what this test changes it to.
        mockMvc.perform(post("/api/v1/admin/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"brandNewAdminPassword789\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminResetPassword_withInvalidToken_shouldReturn400() throws Exception {
        ResetPasswordRequest resetRequest = new ResetPasswordRequest();
        resetRequest.setToken("token-that-does-not-exist");
        resetRequest.setNewPassword("someNewPassword789");

        mockMvc.perform(post("/api/v1/admin/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest());
    }
}
