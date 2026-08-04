package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreatePostMealFeedbackRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PaymentEntryRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PublicFeedbackControllerIntegrationTest {

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

    private String registerAndGetToken(RegisterRestaurantRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-restaurant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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

    private String createTable(String token) throws Exception {
        CreateTableRequest request = new CreateTableRequest();
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createCategory(String token) throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Burgers " + System.nanoTime());
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createProduct(String token, String categoryId) throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName("Cheeseburger " + System.nanoTime());
        request.setPrice(new BigDecimal("20.00"));
        request.setCategoryId(UUID.fromString(categoryId));
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    /** Places a self-order for the table (opens the tab automatically) and returns [itemId, tabId]. */
    private String[] placeTableOrder(String slug, String tableId, String productId) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        MvcResult result = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new String[] {JsonPath.read(body, "$.items[0].id"), JsonPath.read(body, "$.tabId")};
    }

    /** Opens a Balcão (counter) tab with no table and places a staff order on it, returns [itemId, tabId]. */
    private String[] openCounterTabWithOrder(String token, String productId) throws Exception {
        OpenTabRequest openRequest = new OpenTabRequest();
        openRequest.setTableIds(List.of());
        MvcResult openResult = mockMvc.perform(post("/api/v1/tabs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String tabId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.id");

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setItems(List.of(item));
        MvcResult orderResult = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = JsonPath.read(orderResult.getResponse().getContentAsString(), "$.items[0].id");
        return new String[] {itemId, tabId};
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

    private void payTab(String token, String tabId, String paidAmount) throws Exception {
        PaymentEntryRequest entry = new PaymentEntryRequest();
        entry.setPaymentMethod(PaymentMethod.PIX);
        entry.setAmount(new BigDecimal(paidAmount));
        RegisterPaymentsRequest request = new RegisterPaymentsRequest();
        request.setPayments(List.of(entry));
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private String submitFeedbackRequestBody(int rating, String comment) throws Exception {
        CreatePostMealFeedbackRequest request = new CreatePostMealFeedbackRequest();
        request.setRating(rating);
        request.setComment(comment);
        return objectMapper.writeValueAsString(request);
    }

    private String closedTableTab(String token, String slug) throws Exception {
        String tableId = createTable(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        String[] itemAndTab = placeTableOrder(slug, tableId, productId);
        deliverItem(token, itemAndTab[0]);
        payTab(token, itemAndTab[1], "20.00");
        return itemAndTab[1];
    }

    @Test
    void feedbackFlow_forTableTab_shouldSucceedAndBlockResubmission() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tabId = closedTableTab(token, slug);

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/tabs/" + tabId + "/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadySubmitted").value(false))
                .andExpect(jsonPath("$.tableNumbers.length()").value(1))
                .andExpect(jsonPath("$.restaurantName").value("Burger House"));

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tabs/" + tabId + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitFeedbackRequestBody(5, "Ótimo atendimento!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadySubmitted").value(true));

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/tabs/" + tabId + "/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadySubmitted").value(true));

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tabs/" + tabId + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitFeedbackRequestBody(4, "De novo")))
                .andExpect(status().isForbidden());
    }

    @Test
    void feedbackFlow_forCounterTab_shouldReturnEmptyTableNumbers() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        String[] itemAndTab = openCounterTabWithOrder(token, productId);
        deliverItem(token, itemAndTab[0]);
        payTab(token, itemAndTab[1], "20.00");

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/tabs/" + itemAndTab[1] + "/feedback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNumbers.length()").value(0));

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tabs/" + itemAndTab[1] + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitFeedbackRequestBody(3, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadySubmitted").value(true));
    }

    @Test
    void getContext_forOpenTab_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        String[] itemAndTab = placeTableOrder(slug, tableId, productId);

        mockMvc.perform(get("/api/v1/public/menu/" + slug + "/tabs/" + itemAndTab[1] + "/feedback"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getContext_crossTenantSlug_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tabId = closedTableTab(token, slug);

        RegisterRestaurantRequest otherRestaurant = new RegisterRestaurantRequest();
        otherRestaurant.setRestaurantName("Pizza Place");
        otherRestaurant.setOwnerName("Another Owner");
        otherRestaurant.setOwnerEmail("another+" + System.nanoTime() + "@test.com");
        otherRestaurant.setOwnerPassword("password789");
        String otherToken = registerAndGetToken(otherRestaurant);
        String otherSlug = getSlug(otherToken);

        mockMvc.perform(get("/api/v1/public/menu/" + otherSlug + "/tabs/" + tabId + "/feedback"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicMenu_shouldExposeCurrentTabId_onlyWhileTabIsOpen() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String tableId = createTable(token);
        String categoryId = createCategory(token);
        String productId = createProduct(token, categoryId);
        String[] itemAndTab = placeTableOrder(slug, tableId, productId);

        mockMvc.perform(get("/api/v1/public/menu/" + slug).param("tableId", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.currentTabId").value(itemAndTab[1]));

        deliverItem(token, itemAndTab[0]);
        payTab(token, itemAndTab[1], "20.00");

        // Once the tab closes, currentTabId disappears — the client is responsible for having
        // remembered it while it was open, so it can redirect its own session to feedback. A
        // fresh page load at this point (e.g. the next customer at the table) sees no tab at all,
        // never a stray feedback prompt for someone else's meal.
        mockMvc.perform(get("/api/v1/public/menu/" + slug).param("tableId", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.currentTabId").doesNotExist());
    }

    @Test
    void getFeedbackReport_asOwner_shouldReturnAverageAndRecent() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);

        String firstTabId = closedTableTab(token, slug);
        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tabs/" + firstTabId + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitFeedbackRequestBody(4, "Bom")))
                .andExpect(status().isOk());

        String secondTabId = closedTableTab(token, slug);
        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tabs/" + secondTabId + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitFeedbackRequestBody(2, "Regular")))
                .andExpect(status().isOk());

        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/v1/reports/feedback")
                        .header("Authorization", "Bearer " + token)
                        .param("start", today)
                        .param("end", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(3.0))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.recent.length()").value(2));
    }

    @Test
    void getFeedbackEntries_asOwner_shouldPaginate() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);

        for (int i = 1; i <= 3; i++) {
            String tabId = closedTableTab(token, slug);
            mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tabs/" + tabId + "/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(submitFeedbackRequestBody(5, "Avaliação " + i)))
                    .andExpect(status().isOk());
        }

        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/reports/feedback/entries")
                        .header("Authorization", "Bearer " + token)
                        .param("start", today)
                        .param("end", today)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/reports/feedback/entries")
                        .header("Authorization", "Bearer " + token)
                        .param("start", today)
                        .param("end", today)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }
}
