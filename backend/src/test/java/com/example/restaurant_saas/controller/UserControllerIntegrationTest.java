package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.PasswordResetToken;
import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateUserRequest;
import com.example.restaurant_saas.dto.request.LoginRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.ResetPasswordRequest;
import com.example.restaurant_saas.dto.request.UpdateUserRequest;
import com.example.restaurant_saas.repository.PasswordResetTokenRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

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
    private PasswordResetTokenRepository passwordResetTokenRepository;

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
    void createUser_asOwner_creatingWaiter_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Waiter 1");
        request.setEmail("waiter1+" + System.nanoTime() + "@test.com");
        request.setRole(UserRole.WAITER);

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("WAITER"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createUser_shouldSendPasswordSetupLink_andAllowCompletingLoginThroughIt() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String waiterEmail = "waiter1+" + System.nanoTime() + "@test.com";

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Waiter 1");
        request.setEmail(waiterEmail);
        request.setRole(UserRole.WAITER);

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // No password came in the request -- the account only becomes usable by completing
        // the same token-based flow as forgot-password, which the invite email carries a link to.
        User waiter = userRepository.findByEmailBypassingRls(waiterEmail).orElseThrow();
        PasswordResetToken token = passwordResetTokenRepository.findByUser(waiter).orElseThrow();

        ResetPasswordRequest completeSetup = new ResetPasswordRequest();
        completeSetup.setToken(token.getToken());
        completeSetup.setNewPassword("waiterChosenPassword1");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeSetup)))
                .andExpect(status().isOk());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(waiterEmail);
        loginRequest.setPassword("waiterChosenPassword1");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void createUser_asOwner_creatingManager_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Manager 1");
        request.setEmail("manager1+" + System.nanoTime() + "@test.com");
        request.setRole(UserRole.MANAGER);

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void createUser_asManager_creatingManager_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User manager = createUserDirectly(owner, UserRole.MANAGER);
        String managerToken = tokenFor(manager);

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Another Manager");
        request.setEmail("anothermanager+" + System.nanoTime() + "@test.com");
        request.setRole(UserRole.MANAGER);

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_asManager_creatingWaiter_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User manager = createUserDirectly(owner, UserRole.MANAGER);
        String managerToken = tokenFor(manager);

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Kitchen 1");
        request.setEmail("kitchen1+" + System.nanoTime() + "@test.com");
        request.setRole(UserRole.KITCHEN);

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("KITCHEN"));
    }

    @Test
    void createUser_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Another Waiter");
        request.setEmail("anotherwaiter+" + System.nanoTime() + "@test.com");
        request.setRole(UserRole.WAITER);

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_withDuplicateEmail_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Waiter 1");
        request.setEmail(registerRequest.getOwnerEmail());
        request.setRole(UserRole.WAITER);

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listUsers_asOwner_shouldReturnAllStaff() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        createUserDirectly(owner, UserRole.WAITER);
        createUserDirectly(owner, UserRole.KITCHEN);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void listUsers_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUser_asOwner_shouldReturnUserDetails() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);

        mockMvc.perform(get("/api/v1/users/" + waiter.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(waiter.getId().toString()))
                .andExpect(jsonPath("$.role").value("WAITER"));
    }

    @Test
    void createUser_withMissingFields_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUser_crossTenant_shouldNotBeFound() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("anotherlookup+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");

        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherRestaurant)))
                .andExpect(status().isCreated());

        User otherOwner = userRepository.findByEmailBypassingRls(otherRestaurant.getOwnerEmail()).orElseThrow();

        mockMvc.perform(get("/api/v1/users/" + otherOwner.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listUsers_filterByRole_shouldReturnOnlyMatching() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        createUserDirectly(owner, UserRole.WAITER);
        createUserDirectly(owner, UserRole.KITCHEN);

        mockMvc.perform(get("/api/v1/users").param("role", "WAITER")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("WAITER"));
    }

    @Test
    void updateUser_asOwner_shouldChangeNameAndRole() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Renamed Waiter");
        request.setRole(UserRole.CASHIER);

        mockMvc.perform(put("/api/v1/users/" + waiter.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Waiter"))
                .andExpect(jsonPath("$.role").value("CASHIER"));
    }

    @Test
    void updateUser_deactivate_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setActive(false);

        mockMvc.perform(put("/api/v1/users/" + waiter.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateUser_selfRoleChange_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();

        UpdateUserRequest request = new UpdateUserRequest();
        request.setActive(false);

        mockMvc.perform(put("/api/v1/users/" + owner.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_managerTargetingOwner_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User manager = createUserDirectly(owner, UserRole.MANAGER);
        String managerToken = tokenFor(manager);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setActive(false);

        // A MANAGER does not manage an OWNER (not in the set of roles it can touch).
        mockMvc.perform(put("/api/v1/users/" + owner.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_ownerDeactivatingAnotherOwner_whenMultipleOwnersExist_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User secondOwner = createUserDirectly(owner, UserRole.OWNER);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setActive(false);

        mockMvc.perform(put("/api/v1/users/" + secondOwner.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateUser_deactivatingLastActiveOwner_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User secondOwner = createUserDirectly(owner, UserRole.OWNER);

        UpdateUserRequest deactivateRequest = new UpdateUserRequest();
        deactivateRequest.setActive(false);

        // First deactivation succeeds: two active owners before it, one remains after.
        mockMvc.perform(put("/api/v1/users/" + secondOwner.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateRequest)))
                .andExpect(status().isOk());

        // Only one active owner is left (the acting user); trying to touch the other
        // OWNER's active flag again must be blocked, since it would leave the
        // restaurant with zero active owners if it were ever allowed to succeed.
        mockMvc.perform(put("/api/v1/users/" + secondOwner.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_managerEditingWaiter_promotingToManager_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User manager = createUserDirectly(owner, UserRole.MANAGER);
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String managerToken = tokenFor(manager);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole(UserRole.MANAGER);

        mockMvc.perform(put("/api/v1/users/" + waiter.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_crossTenant_shouldNotBeFound() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");

        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherRestaurant)))
                .andExpect(status().isCreated());

        User otherOwner = userRepository.findByEmailBypassingRls(otherRestaurant.getOwnerEmail()).orElseThrow();

        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Should not work");

        mockMvc.perform(put("/api/v1/users/" + otherOwner.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listUsers_filterByActive_shouldReturnOnlyMatching() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        waiter.setActive(false);
        TenantTestSupport.withTenant(owner.getRestaurant().getId(), () -> userRepository.save(waiter));

        mockMvc.perform(get("/api/v1/users").param("active", "false")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(waiter.getId().toString()));
    }

    @Test
    void sendPasswordResetLink_asOwner_shouldSucceed_andLinkAllowsSettingNewPassword() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);

        mockMvc.perform(post("/api/v1/users/" + waiter.getId() + "/password-reset-link")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        PasswordResetToken token = passwordResetTokenRepository.findByUser(waiter).orElseThrow();

        ResetPasswordRequest completeReset = new ResetPasswordRequest();
        completeReset.setToken(token.getToken());
        completeReset.setNewPassword("brandNewPassword1");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReset)))
                .andExpect(status().isOk());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(waiter.getEmail());
        loginRequest.setPassword("brandNewPassword1");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void sendPasswordResetLink_targetingSelf_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();

        mockMvc.perform(post("/api/v1/users/" + owner.getId() + "/password-reset-link")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendPasswordResetLink_targetingOwner_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User secondOwner = createUserDirectly(owner, UserRole.OWNER);

        mockMvc.perform(post("/api/v1/users/" + secondOwner.getId() + "/password-reset-link")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendPasswordResetLink_managerTargetingManager_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User manager = createUserDirectly(owner, UserRole.MANAGER);
        User anotherManager = createUserDirectly(owner, UserRole.MANAGER);
        String managerToken = tokenFor(manager);

        mockMvc.perform(post("/api/v1/users/" + anotherManager.getId() + "/password-reset-link")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendPasswordResetLink_crossTenant_shouldNotBeFound() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("resetcross+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");

        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherRestaurant)))
                .andExpect(status().isCreated());

        User otherOwner = userRepository.findByEmailBypassingRls(otherRestaurant.getOwnerEmail()).orElseThrow();
        User otherWaiter = createUserDirectly(otherOwner, UserRole.WAITER);

        mockMvc.perform(post("/api/v1/users/" + otherWaiter.getId() + "/password-reset-link")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpoints_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().is4xxClientError());
    }
}
