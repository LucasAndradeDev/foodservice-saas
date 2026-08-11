package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.dto.request.UpdateProductRequest;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

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

    private String createCategory(String ownerToken, String name) throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName(name);
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
    void createProduct_asOwner_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setDescription("Beef patty with cheese");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cheeseburger"))
                .andExpect(jsonPath("$.price").value(25.90))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.featured").value(false));
    }

    @Test
    void createProduct_withImageUrl_shouldPersistAndAllowUpdate() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));
        request.setImageUrl("https://cdn.test.com/cheeseburger.jpg");

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.test.com/cheeseburger.jpg"))
                .andReturn();
        String productId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        UpdateProductRequest updateRequest = new UpdateProductRequest();
        updateRequest.setImageUrl("https://cdn.test.com/cheeseburger-v2.jpg");

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.test.com/cheeseburger-v2.jpg"));
    }

    @Test
    void createProduct_withCategoryFromAnotherRestaurant_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

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
        String otherCategoryId = createCategory(otherToken, "Pizzas");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Margherita");
        request.setPrice(new BigDecimal("35.00"));
        request.setCategoryId(java.util.UUID.fromString(otherCategoryId));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_withCostPrice_shouldPersist() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCostPrice(new BigDecimal("9.50"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.costPrice").value(9.50));
    }

    @Test
    void createProduct_withoutCostPrice_shouldReturnNullCostPrice() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.costPrice").doesNotExist());
    }

    @Test
    void createProduct_withNegativeCostPrice_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCostPrice(new BigDecimal("-1.00"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProduct_setCostPrice_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateProductRequest updateRequest = new UpdateProductRequest();
        updateRequest.setCostPrice(new BigDecimal("9.50"));

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costPrice").value(9.50));
    }

    @Test
    void createProduct_withNonPositivePrice_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Free Sample");
        request.setPrice(BigDecimal.ZERO);
        request.setCategoryId(java.util.UUID.fromString(categoryId));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_withDuplicateNameCaseInsensitive_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CreateProductRequest duplicateRequest = new CreateProductRequest();
        duplicateRequest.setName("CHEESEBURGER");
        duplicateRequest.setPrice(new BigDecimal("29.90"));
        duplicateRequest.setCategoryId(java.util.UUID.fromString(categoryId));
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_withDuplicateNameInDifferentCategory_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String burgersId = createCategory(ownerToken, "Burgers");
        String comboId = createCategory(ownerToken, "Combos");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(burgersId));
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CreateProductRequest duplicateRequest = new CreateProductRequest();
        duplicateRequest.setName("Cheeseburger");
        duplicateRequest.setPrice(new BigDecimal("29.90"));
        duplicateRequest.setCategoryId(java.util.UUID.fromString(comboId));
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProduct_keepingSameName_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateProductRequest updateRequest = new UpdateProductRequest();
        updateRequest.setName("Cheeseburger");
        updateRequest.setPrice(new BigDecimal("27.90"));

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(27.90));
    }

    @Test
    void createProduct_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listProducts_asWaiter_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listProducts_filterByCategory_shouldReturnOnlyMatching() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String burgersId = createCategory(ownerToken, "Burgers");
        String drinksId = createCategory(ownerToken, "Drinks");

        CreateProductRequest burger = new CreateProductRequest();
        burger.setName("Cheeseburger");
        burger.setPrice(new BigDecimal("25.90"));
        burger.setCategoryId(java.util.UUID.fromString(burgersId));
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(burger)))
                .andExpect(status().isCreated());

        CreateProductRequest soda = new CreateProductRequest();
        soda.setName("Soda");
        soda.setPrice(new BigDecimal("6.00"));
        soda.setCategoryId(java.util.UUID.fromString(drinksId));
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(soda)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products").param("categoryId", drinksId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Soda"));
    }

    @Test
    void listProducts_searchByName_shouldReturnOnlyMatching() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest cheeseburger = new CreateProductRequest();
        cheeseburger.setName("Cheeseburger");
        cheeseburger.setPrice(new BigDecimal("25.90"));
        cheeseburger.setCategoryId(java.util.UUID.fromString(categoryId));
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cheeseburger)))
                .andExpect(status().isCreated());

        CreateProductRequest baconBurger = new CreateProductRequest();
        baconBurger.setName("Bacon Burger");
        baconBurger.setPrice(new BigDecimal("28.90"));
        baconBurger.setCategoryId(java.util.UUID.fromString(categoryId));
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(baconBurger)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products").param("search", "cheese")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Cheeseburger"));
    }

    @Test
    void getProduct_crossTenant_shouldNotBeFound() throws Exception {
        String ownerToken = registerOwnerAndGetToken();

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another2+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        MvcResult otherResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherRestaurant)))
                .andExpect(status().isCreated())
                .andReturn();
        String otherToken = JsonPath.read(otherResult.getResponse().getContentAsString(), "$.accessToken");
        String otherCategoryId = createCategory(otherToken, "Pizzas");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Margherita");
        request.setPrice(new BigDecimal("35.00"));
        request.setCategoryId(java.util.UUID.fromString(otherCategoryId));
        MvcResult productResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String otherProductId = JsonPath.read(productResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/products/" + otherProductId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProduct_asOwner_shouldChangePriceAndCategory() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String burgersId = createCategory(ownerToken, "Burgers");
        String comboId = createCategory(ownerToken, "Combos");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(burgersId));
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateProductRequest updateRequest = new UpdateProductRequest();
        updateRequest.setPrice(new BigDecimal("29.90"));
        updateRequest.setCategoryId(java.util.UUID.fromString(comboId));

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(29.90))
                .andExpect(jsonPath("$.categoryId").value(comboId));
    }

    @Test
    void updateProduct_deactivate_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateProductRequest updateRequest = new UpdateProductRequest();
        updateRequest.setActive(false);

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateProduct_markSoldOut_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateProductRequest markSoldOut = new UpdateProductRequest();
        markSoldOut.setSoldOut(true);

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(markSoldOut)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.soldOutToday").value(true));

        UpdateProductRequest unmarkSoldOut = new UpdateProductRequest();
        unmarkSoldOut.setSoldOut(false);

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unmarkSoldOut)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldOutToday").value(false));
    }

    @Test
    void updateProduct_markFeatured_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateProductRequest markFeatured = new UpdateProductRequest();
        markFeatured.setFeatured(true);

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(markFeatured)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").value(true));

        UpdateProductRequest unmarkFeatured = new UpdateProductRequest();
        unmarkFeatured.setFeatured(false);

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unmarkFeatured)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").value(false));
    }

    @Test
    void updateProduct_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        UpdateProductRequest updateRequest = new UpdateProductRequest();
        updateRequest.setName("Should not work");

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProduct_withCategoryFromAnotherRestaurant_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(java.util.UUID.fromString(categoryId));
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another3+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        MvcResult otherResult = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherRestaurant)))
                .andExpect(status().isCreated())
                .andReturn();
        String otherToken = JsonPath.read(otherResult.getResponse().getContentAsString(), "$.accessToken");
        String otherCategoryId = createCategory(otherToken, "Pizzas");

        UpdateProductRequest updateRequest = new UpdateProductRequest();
        updateRequest.setCategoryId(java.util.UUID.fromString(otherCategoryId));

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
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

    private String openTabAndGetId(String token, String tableId) throws Exception {
        OpenTabRequest request = new OpenTabRequest();
        request.setTableIds(List.of(UUID.fromString(tableId)));
        MvcResult result = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void orderProductOnce(String token, String tabId, String productId) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void deleteProduct_neverOrdered_shouldSucceed() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(UUID.fromString(categoryId));
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProduct_alreadyOrdered_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(UUID.fromString(categoryId));
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        String tableId = createTableAndGetId(ownerToken);
        String tabId = openTabAndGetId(ownerToken, tableId);
        orderProductOnce(ownerToken, tabId, productId);

        mockMvc.perform(delete("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteProduct_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);
        String categoryId = createCategory(ownerToken, "Burgers");

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger");
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(UUID.fromString(categoryId));
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadImage_withValidJpeg_shouldReturnUrl() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 'f', 'a', 'k', 'e'});

        mockMvc.perform(multipart("/api/v1/products/upload-image")
                        .file(file)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/api/v1/public/uploads/products/")))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.endsWith(".jpg")));
    }

    @Test
    void uploadImage_withInvalidContentType_shouldReturn400() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "fake-pdf-bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/products/upload-image")
                        .file(file)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadImage_asWaiter_shouldBeForbidden() throws Exception {
        String ownerToken = registerOwnerAndGetToken();
        User owner = userRepository.findByEmailBypassingRls(registerRequest.getOwnerEmail()).orElseThrow();
        User waiter = createUserDirectly(owner, UserRole.WAITER);
        String waiterToken = tokenFor(waiter);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 'f', 'a', 'k', 'e'});

        mockMvc.perform(multipart("/api/v1/products/upload-image")
                        .file(file)
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }
}
