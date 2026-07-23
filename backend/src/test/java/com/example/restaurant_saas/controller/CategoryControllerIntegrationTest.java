package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateCategoryRequest;
import com.example.restaurant_saas.dto.request.UpdateProductRequest;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryControllerIntegrationTest {

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
    void createCategory_asOwner_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Burgers");

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Burgers"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createCategory_withDuplicateNameCaseInsensitive_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Drinks");

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CreateCategoryRequest duplicateRequest = new CreateCategoryRequest();
        duplicateRequest.setName("DRINKS");

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCategory_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Desserts");

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCategory_asManager_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User manager = createUserDirectly(owner, UserRole.MANAGER);
        String managerToken = tokenFor(manager);

        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Pizzas");

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void listCategories_asWaiter_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Burgers");
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listCategories_withoutToken_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listCategories_filterByActive_shouldReturnOnlyMatching() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateCategoryRequest activeRequest = new CreateCategoryRequest();
        activeRequest.setName("Burgers");
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activeRequest)))
                .andExpect(status().isCreated());

        CreateCategoryRequest toDeactivateRequest = new CreateCategoryRequest();
        toDeactivateRequest.setName("Drinks");
        MvcResult createResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toDeactivateRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String categoryId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateCategoryRequest deactivateRequest = new UpdateCategoryRequest();
        deactivateRequest.setActive(false);
        mockMvc.perform(put("/api/v1/categories/" + categoryId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/categories").param("active", "true")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Burgers"));
    }

    @Test
    void getCategory_crossTenant_shouldNotBeFound() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        String otherToken = registerAndGetToken(otherRestaurant);

        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Pizzas");
        MvcResult createResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String otherCategoryId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/categories/" + otherCategoryId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCategory_asOwner_shouldChangeNameAndActive() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Burgers");
        MvcResult createResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String categoryId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateCategoryRequest updateRequest = new UpdateCategoryRequest();
        updateRequest.setName("Classic Burgers");
        updateRequest.setActive(false);

        mockMvc.perform(put("/api/v1/categories/" + categoryId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Classic Burgers"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateCategory_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmail(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Burgers");
        MvcResult createResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String categoryId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateCategoryRequest updateRequest = new UpdateCategoryRequest();
        updateRequest.setName("Should not work");

        mockMvc.perform(put("/api/v1/categories/" + categoryId)
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCategory_renameToExistingName_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateCategoryRequest firstRequest = new CreateCategoryRequest();
        firstRequest.setName("Burgers");
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        CreateCategoryRequest secondRequest = new CreateCategoryRequest();
        secondRequest.setName("Drinks");
        MvcResult secondResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String secondCategoryId = JsonPath.read(secondResult.getResponse().getContentAsString(), "$.id");

        UpdateCategoryRequest renameRequest = new UpdateCategoryRequest();
        renameRequest.setName("Burgers");

        mockMvc.perform(put("/api/v1/categories/" + secondCategoryId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(renameRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCategory_keepingSameName_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Burgers");
        MvcResult createResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String categoryId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateCategoryRequest updateRequest = new UpdateCategoryRequest();
        updateRequest.setName("Burgers");
        updateRequest.setActive(false);

        mockMvc.perform(put("/api/v1/categories/" + categoryId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateCategory_deactivatingWithActiveProducts_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateCategoryRequest categoryRequest = new CreateCategoryRequest();
        categoryRequest.setName("Burgers");
        MvcResult categoryResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoryRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String categoryId = JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.id");

        CreateProductRequest productRequest = new CreateProductRequest();
        productRequest.setName("Cheeseburger");
        productRequest.setPrice(new BigDecimal("25.90"));
        productRequest.setCategoryId(UUID.fromString(categoryId));
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated());

        UpdateCategoryRequest deactivateRequest = new UpdateCategoryRequest();
        deactivateRequest.setActive(false);

        mockMvc.perform(put("/api/v1/categories/" + categoryId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCategory_deactivatingAfterProductsDeactivated_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        CreateCategoryRequest categoryRequest = new CreateCategoryRequest();
        categoryRequest.setName("Burgers");
        MvcResult categoryResult = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoryRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String categoryId = JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.id");

        CreateProductRequest productRequest = new CreateProductRequest();
        productRequest.setName("Cheeseburger");
        productRequest.setPrice(new BigDecimal("25.90"));
        productRequest.setCategoryId(UUID.fromString(categoryId));
        MvcResult productResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(productResult.getResponse().getContentAsString(), "$.id");

        UpdateProductRequest deactivateProductRequest = new UpdateProductRequest();
        deactivateProductRequest.setActive(false);
        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateProductRequest)))
                .andExpect(status().isOk());

        UpdateCategoryRequest deactivateCategoryRequest = new UpdateCategoryRequest();
        deactivateCategoryRequest.setActive(false);

        mockMvc.perform(put("/api/v1/categories/" + categoryId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivateCategoryRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    private String registerAndGetToken(RegisterRestaurantRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
