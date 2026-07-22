package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateUserRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateUserRequest;
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

    private RegisterRestaurantRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Burger House");
        registerRequest.setPhone("11999999999");
        registerRequest.setAddress("Rua 1, 100");
        registerRequest.setOwnerName("Dono");
        registerRequest.setOwnerEmail("dono+" + System.nanoTime() + "@teste.com");
        registerRequest.setOwnerPassword("senha123");
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
                .email(role.name().toLowerCase() + "+" + System.nanoTime() + "@teste.com")
                .password(passwordEncoder.encode("senha123"))
                .role(role)
                .active(true)
                .build();
        return userRepository.save(user);
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(new UserDetailsImpl(user));
    }

    @Test
    void createUser_asOwner_creatingWaiter_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Garcom 1");
        request.setEmail("garcom1+" + System.nanoTime() + "@teste.com");
        request.setPassword("senha123");
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
    void createUser_asOwner_creatingManager_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Gerente 1");
        request.setEmail("gerente1+" + System.nanoTime() + "@teste.com");
        request.setPassword("senha123");
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
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User manager = createUserDirectly(owner, UserRole.MANAGER);
        String managerToken = tokenFor(manager);

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Outro Gerente");
        request.setEmail("outrogerente+" + System.nanoTime() + "@teste.com");
        request.setPassword("senha123");
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
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User manager = createUserDirectly(owner, UserRole.MANAGER);
        String managerToken = tokenFor(manager);

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Cozinha 1");
        request.setEmail("cozinha1+" + System.nanoTime() + "@teste.com");
        request.setPassword("senha123");
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
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateUserRequest request = new CreateUserRequest();
        request.setName("Outro Garcom");
        request.setEmail("outrogarcom+" + System.nanoTime() + "@teste.com");
        request.setPassword("senha123");
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
        request.setName("Garcom 1");
        request.setEmail(registerRequest.getOwnerEmail());
        request.setPassword("senha123");
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
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        createUserDirectly(owner, UserRole.WAITER);
        createUserDirectly(owner, UserRole.KITCHEN);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void listUsers_filterByRole_shouldReturnOnlyMatching() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
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
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Garcom Renomeado");
        request.setRole(UserRole.CASHIER);

        mockMvc.perform(put("/api/v1/users/" + waiter.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Garcom Renomeado"))
                .andExpect(jsonPath("$.role").value("CASHIER"));
    }

    @Test
    void updateUser_deactivate_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
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
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();

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
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User manager = createUserDirectly(owner, UserRole.MANAGER);
        String managerToken = tokenFor(manager);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setActive(false);

        // MANAGER não gerencia OWNER (não está no conjunto de papéis que ele pode tocar).
        mockMvc.perform(put("/api/v1/users/" + owner.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_ownerDeactivatingAnotherOwner_whenMultipleOwnersExist_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
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
    void updateUser_managerEditingWaiter_promotingToManager_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
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
        otherRestaurant.setOwnerName("Outro Dono");
        otherRestaurant.setOwnerEmail("outro+" + System.nanoTime() + "@teste.com");
        otherRestaurant.setOwnerPassword("senha789");

        mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherRestaurant)))
                .andExpect(status().isCreated());

        User otherOwner = userRepository.findByEmail(otherRestaurant.getOwnerEmail()).orElseThrow();

        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Não deveria funcionar");

        mockMvc.perform(put("/api/v1/users/" + otherOwner.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpoints_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().is4xxClientError());
    }
}
