package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateProductRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MenuControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private String getSlug(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/restaurants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
    }

    private String createCategory(String token, String name) throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName(name);
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createProduct(String token, String categoryId, String name, String price, String imageUrl) throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName(name);
        request.setPrice(new BigDecimal(price));
        request.setCategoryId(UUID.fromString(categoryId));
        request.setImageUrl(imageUrl);
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void deactivateProduct(String token, String productId) throws Exception {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setActive(false);
        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void getPublicMenu_withoutAuthentication_shouldReturnActiveCategoriesAndProducts() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        createProduct(token, categoryId, "Cheeseburger", "25.90", "https://cdn.test.com/cheeseburger.jpg");

        mockMvc.perform(get("/api/v1/public/menu/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Burger House"))
                .andExpect(jsonPath("$.categories", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.categories[0].name").value("Burgers"))
                .andExpect(jsonPath("$.categories[0].products", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.categories[0].products[0].name").value("Cheeseburger"))
                .andExpect(jsonPath("$.categories[0].products[0].price").value(25.90))
                .andExpect(jsonPath("$.categories[0].products[0].imageUrl").value("https://cdn.test.com/cheeseburger.jpg"));
    }

    @Test
    void getPublicMenu_withInactiveProduct_shouldExcludeIt() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90", null);
        deactivateProduct(token, productId);

        mockMvc.perform(get("/api/v1/public/menu/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void getPublicMenu_withEmptyCategory_shouldExcludeIt() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryWithProduct = createCategory(token, "Burgers");
        createProduct(token, categoryWithProduct, "Cheeseburger", "25.90", null);
        createCategory(token, "Drinks");

        mockMvc.perform(get("/api/v1/public/menu/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.categories[0].name").value("Burgers"));
    }

    @Test
    void getPublicMenu_withUnknownSlug_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/public/menu/does-not-exist"))
                .andExpect(status().isBadRequest());
    }
}
