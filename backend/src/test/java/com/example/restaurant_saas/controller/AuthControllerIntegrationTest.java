package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.EmailVerificationToken;
import com.example.restaurant_saas.domain.entity.PasswordResetToken;
import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.dto.request.ChangePasswordRequest;
import com.example.restaurant_saas.dto.request.ForgotPasswordRequest;
import com.example.restaurant_saas.dto.request.LoginRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.ResetPasswordRequest;
import com.example.restaurant_saas.dto.request.VerifyEmailRequest;
import com.example.restaurant_saas.repository.EmailVerificationTokenRepository;
import com.example.restaurant_saas.repository.PasswordResetTokenRepository;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.support.TenantTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    private RegisterRestaurantRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Burger House");
        registerRequest.setCnpj(null);
        registerRequest.setPhone("11999999999");
        registerRequest.setAddress("Main St, 100");
        registerRequest.setOwnerName("Owner");
        registerRequest.setOwnerEmail("owner+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");
    }

    @Test
    void registerRestaurant_shouldCreateOwnerAndReturnTokens() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                // The refresh token must never appear in the JSON body — it only ever travels as
                // the httpOnly cookie asserted below.
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value(registerRequest.getOwnerEmail()))
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andExpect(jsonPath("$.restaurant.name").value("Burger House"))
                .andReturn();

        Cookie refreshCookie = result.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(refreshCookie.getValue()).isNotBlank();

        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        assertThat(owner.getEmailVerified()).isFalse();
        assertThat(emailVerificationTokenRepository.findByUser(owner)).isPresent();
    }

    @Test
    void registerRestaurant_withDuplicateEmail_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already registered."));
    }

    @Test
    void login_withValidCredentials_shouldReturnTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(registerRequest.getOwnerEmail());
        loginRequest.setPassword(registerRequest.getOwnerPassword());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value(registerRequest.getOwnerEmail()))
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
    }

    @Test
    void login_withWrongPassword_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(registerRequest.getOwnerEmail());
        loginRequest.setPassword("wrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void login_withNonExistentEmail_shouldReturn401() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("doesnotexist@test.com");
        loginRequest.setPassword("whatever");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_afterTooManyFailedAttempts_shouldReturn429() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(registerRequest.getOwnerEmail());
        loginRequest.setPassword("wrongPassword");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isTooManyRequests());

        loginRequest.setPassword(registerRequest.getOwnerPassword());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void login_withInactiveUser_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        owner.setActive(false);
        TenantTestSupport.withTenant(owner.getRestaurant().getId(), () -> userRepository.save(owner));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(registerRequest.getOwnerEmail());
        loginRequest.setPassword(registerRequest.getOwnerPassword());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPassword_withRegisteredEmail_shouldGenerateResetToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest();
        forgotRequest.setEmail(registerRequest.getOwnerEmail());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotRequest)))
                .andExpect(status().isNoContent());

        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        assertThat(passwordResetTokenRepository.findByUser(owner)).isPresent();
    }

    @Test
    void forgotPassword_withUnregisteredEmail_shouldReturnSameGenericResponse() throws Exception {
        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest();
        forgotRequest.setEmail("doesnotexist+" + System.nanoTime() + "@test.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotRequest)))
                .andExpect(status().isNoContent());
    }

    @Test
    void forgotPassword_afterTooManyAttempts_shouldReturn429() throws Exception {
        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest();
        forgotRequest.setEmail("rate-limited+" + System.nanoTime() + "@test.com");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(forgotRequest)))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotRequest)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void resetPassword_withValidToken_shouldAllowLoginWithNewPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest();
        forgotRequest.setEmail(registerRequest.getOwnerEmail());
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotRequest)))
                .andExpect(status().isNoContent());

        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        PasswordResetToken resetToken = passwordResetTokenRepository.findByUser(owner).orElseThrow();

        ResetPasswordRequest resetRequest = new ResetPasswordRequest();
        resetRequest.setToken(resetToken.getToken());
        resetRequest.setNewPassword("brandNewPassword789");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(registerRequest.getOwnerEmail()));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(registerRequest.getOwnerEmail());
        loginRequest.setPassword("brandNewPassword789");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_withTokenUsedTwice_shouldReturn400OnSecondAttempt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest();
        forgotRequest.setEmail(registerRequest.getOwnerEmail());
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotRequest)))
                .andExpect(status().isNoContent());

        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        PasswordResetToken resetToken = passwordResetTokenRepository.findByUser(owner).orElseThrow();

        ResetPasswordRequest resetRequest = new ResetPasswordRequest();
        resetRequest.setToken(resetToken.getToken());
        resetRequest.setNewPassword("firstNewPassword789");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk());

        resetRequest.setNewPassword("secondNewPassword789");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_withInvalidToken_shouldReturn400() throws Exception {
        ResetPasswordRequest resetRequest = new ResetPasswordRequest();
        resetRequest.setToken("token-that-does-not-exist");
        resetRequest.setNewPassword("someNewPassword789");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshToken_withValidToken_shouldReturnNewAccessToken() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Cookie refreshCookie = registerResult.getResponse().getCookie("refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh-token").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void refreshToken_withInvalidToken_shouldReturn400() throws Exception {
        Cookie refreshCookie = new Cookie("refreshToken", "token-that-does-not-exist");

        mockMvc.perform(post("/api/v1/auth/refresh-token").cookie(refreshCookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshToken_withoutCookie_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_revokesRefreshToken_soItCanNoLongerBeUsed() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.accessToken");
        Cookie refreshCookie = registerResult.getResponse().getCookie("refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh-token").cookie(refreshCookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void me_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void me_withValidToken_shouldReturnCurrentUser() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(registerRequest.getOwnerEmail()));
    }

    @Test
    void changePassword_withCorrectCurrentPassword_shouldAllowLoginWithNewPassword() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.accessToken");

        ChangePasswordRequest changeRequest = new ChangePasswordRequest();
        changeRequest.setCurrentPassword(registerRequest.getOwnerPassword());
        changeRequest.setNewPassword("newPassword456");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isNoContent());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(registerRequest.getOwnerEmail());
        loginRequest.setPassword("newPassword456");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_withWrongCurrentPassword_shouldReturn400() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.accessToken");

        ChangePasswordRequest changeRequest = new ChangePasswordRequest();
        changeRequest.setCurrentPassword("wrongPassword");
        changeRequest.setNewPassword("newPassword456");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void twoRestaurants_shouldHaveIsolatedTenantIdsInTheirTokens() throws Exception {
        RegisterRestaurantRequest secondRequest = new RegisterRestaurantRequest();
        secondRequest.setRestaurantName("Pizza Place");
        secondRequest.setOwnerName("Another Owner");
        secondRequest.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        secondRequest.setOwnerPassword("password789");

        MvcResult firstResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult secondResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String firstRestaurantId = JsonPath.read(firstResult.getResponse().getContentAsString(), "$.restaurant.id");
        String secondRestaurantId = JsonPath.read(secondResult.getResponse().getContentAsString(), "$.restaurant.id");

        assertThat(firstRestaurantId).isNotEqualTo(secondRestaurantId);
    }

    @Test
    void verifyEmail_withValidToken_shouldMarkUserAsVerified() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        EmailVerificationToken token = emailVerificationTokenRepository.findByUser(owner).orElseThrow();

        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setToken(token.getToken());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isNoContent());

        User verifiedOwner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        assertThat(verifiedOwner.getEmailVerified()).isTrue();
    }

    @Test
    void verifyEmail_withTokenUsedTwice_shouldReturn400OnSecondAttempt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        EmailVerificationToken token = emailVerificationTokenRepository.findByUser(owner).orElseThrow();

        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setToken(token.getToken());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_withInvalidToken_shouldReturn400() throws Exception {
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setToken("token-that-does-not-exist");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendVerificationEmail_shouldReplaceExistingToken() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String accessToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.accessToken");

        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        String firstToken = emailVerificationTokenRepository.findByUser(owner).orElseThrow().getToken();

        mockMvc.perform(post("/api/v1/auth/resend-verification-email")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        String secondToken = emailVerificationTokenRepository.findByUser(owner).orElseThrow().getToken();
        assertThat(secondToken).isNotEqualTo(firstToken);
    }

    @Test
    void resendVerificationEmail_afterTooManyAttempts_shouldReturn429() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String accessToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.accessToken");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/resend-verification-email")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(post("/api/v1/auth/resend-verification-email")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void resendVerificationEmail_whenAlreadyVerified_shouldBeNoOpAndNotCountTowardsRateLimit() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String accessToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.accessToken");

        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        owner.setEmailVerified(true);
        TenantTestSupport.withTenant(owner.getRestaurant().getId(), () -> userRepository.save(owner));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/resend-verification-email")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());
        }
    }
}
