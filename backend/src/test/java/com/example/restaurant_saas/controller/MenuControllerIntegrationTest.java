package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.PaymentEntryRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
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
import java.util.List;
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

    private String createTable(String token, int number) throws Exception {
        CreateTableRequest request = new CreateTableRequest();
        request.setNumber(number);
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String placeOrder(String slug, String tableId, String productId) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(java.util.List.of(item));

        MvcResult result = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");
    }

    private String[] placeOrderAndGetItemIdAndTabId(String slug, String tableId, String productId) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(java.util.List.of(item));

        MvcResult result = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new String[] {JsonPath.read(body, "$.items[0].id"), JsonPath.read(body, "$.tabId")};
    }

    private void payTab(String token, String tabId, String paymentMethod, String paidAmount) throws Exception {
        PaymentEntryRequest entry = new PaymentEntryRequest();
        entry.setPaymentMethod(PaymentMethod.valueOf(paymentMethod));
        entry.setAmount(new BigDecimal(paidAmount));
        RegisterPaymentsRequest request = new RegisterPaymentsRequest();
        request.setPayments(List.of(entry));
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void deliverItem(String token, String itemId) throws Exception {
        for (String status : new String[] {"PREPARING", "READY", "DELIVERED"}) {
            mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + status + "\"}"))
                    .andExpect(status().isOk());
        }
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
    void getPublicMenu_withSoldOutProduct_shouldStillListItButFlagged() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90", null);

        UpdateProductRequest markSoldOut = new UpdateProductRequest();
        markSoldOut.setSoldOut(true);
        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(markSoldOut)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/menu/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].products", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.categories[0].products[0].soldOut").value(true));
    }

    @Test
    void getPublicMenu_withFeaturedProduct_shouldFlagIt() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90", null);

        UpdateProductRequest markFeatured = new UpdateProductRequest();
        markFeatured.setFeatured(true);
        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(markFeatured)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/menu/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].products[0].featured").value(true));
    }

    @Test
    void getPublicMenu_withoutRecentSales_shouldReportBestsellerFalse() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        createProduct(token, categoryId, "Cheeseburger", "25.90", null);

        mockMvc.perform(get("/api/v1/public/menu/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].products[0].bestseller").value(false));
    }

    @Test
    void getPublicMenu_withRecentPaidSale_shouldFlagBestseller() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token, 1);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90", null);

        String[] itemAndTab = placeOrderAndGetItemIdAndTabId(slug, tableId, productId);
        deliverItem(token, itemAndTab[0]);
        payTab(token, itemAndTab[1], "PIX", "25.90");

        mockMvc.perform(get("/api/v1/public/menu/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].products[0].bestseller").value(true));
    }

    @Test
    void getPublicMenu_withModifierGroups_shouldExposeThemNestedOnTheProduct() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Pizzas");
        String productId = createProduct(token, categoryId, "Pizza Calabresa", "40.00", null);

        com.example.restaurant_saas.dto.request.ModifierOptionInput small =
                new com.example.restaurant_saas.dto.request.ModifierOptionInput();
        small.setName("P");
        small.setPriceDelta(BigDecimal.ZERO);
        com.example.restaurant_saas.dto.request.ModifierOptionInput large =
                new com.example.restaurant_saas.dto.request.ModifierOptionInput();
        large.setName("G");
        large.setPriceDelta(new BigDecimal("10.00"));

        com.example.restaurant_saas.dto.request.CreateModifierGroupRequest groupRequest =
                new com.example.restaurant_saas.dto.request.CreateModifierGroupRequest();
        groupRequest.setName("Tamanho");
        groupRequest.setSelectionType(com.example.restaurant_saas.domain.enums.ModifierSelectionType.SINGLE);
        groupRequest.setRequired(true);
        groupRequest.setOptions(java.util.List.of(small, large));

        mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(groupRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/public/menu/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].products[0].modifierGroups", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.categories[0].products[0].modifierGroups[0].name").value("Tamanho"))
                .andExpect(jsonPath("$.categories[0].products[0].modifierGroups[0].options", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.categories[0].products[0].modifierGroups[0].options[1].priceDelta").value(10.00));
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
    void getPublicMenu_withTableAndNoOrders_shouldReportHasDeliveredItemsFalse() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token, 1);

        mockMvc.perform(get("/api/v1/public/menu/" + slug).param("tableId", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.hasDeliveredItems").value(false))
                .andExpect(jsonPath("$.table.orderItems", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void getPublicMenu_withOrderStillInKitchen_shouldReportHasDeliveredItemsFalse() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token, 1);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90", null);

        placeOrder(slug, tableId, productId);

        mockMvc.perform(get("/api/v1/public/menu/" + slug).param("tableId", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.hasDeliveredItems").value(false))
                .andExpect(jsonPath("$.table.orderItems", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.table.orderItems[0].productName").value("Cheeseburger"))
                .andExpect(jsonPath("$.table.orderItems[0].quantity").value(1))
                .andExpect(jsonPath("$.table.orderItems[0].status").value("PENDING"));
    }

    @Test
    void getPublicMenu_withDeliveredItem_shouldReportHasDeliveredItemsTrue() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token, 1);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90", null);

        String itemId = placeOrder(slug, tableId, productId);
        deliverItem(token, itemId);

        mockMvc.perform(get("/api/v1/public/menu/" + slug).param("tableId", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.hasDeliveredItems").value(true))
                .andExpect(jsonPath("$.table.orderItems[0].status").value("DELIVERED"));
    }

    @Test
    void getPublicMenu_withoutDeliveryHistory_shouldReportNullEstimatedWait() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        createProduct(token, categoryId, "Cheeseburger", "25.90", null);

        mockMvc.perform(get("/api/v1/public/menu/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].products[0].estimatedWaitMinutes").doesNotExist());
    }

    @Test
    void getPublicMenu_withDeliveryHistory_shouldReportEstimatedWaitInMinutes() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token, 1);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90", null);

        String itemId = placeOrder(slug, tableId, productId);
        deliverItem(token, itemId);

        mockMvc.perform(get("/api/v1/public/menu/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].products[0].estimatedWaitMinutes").value(0));
    }

    @Test
    void getPublicMenu_withUnknownSlug_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/public/menu/does-not-exist"))
                .andExpect(status().isBadRequest());
    }
}
