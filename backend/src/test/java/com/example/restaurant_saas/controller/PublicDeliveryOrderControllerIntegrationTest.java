package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateDeliveryZoneRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.PaymentEntryRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.repository.DeliveryDetailsRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.support.TenantTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PublicDeliveryOrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private DeliveryDetailsRepository deliveryDetailsRepository;

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

    private String createProduct(String token, String categoryId, String name, String price) throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName(name);
        request.setPrice(new BigDecimal(price));
        request.setCategoryId(UUID.fromString(categoryId));
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void createDeliveryZone(String token, String neighborhood, String fee) throws Exception {
        CreateDeliveryZoneRequest request = new CreateDeliveryZoneRequest();
        request.setNeighborhood(neighborhood);
        request.setFee(new BigDecimal(fee));
        mockMvc.perform(post("/api/v1/delivery-zones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private ObjectNode deliveryOrderBody(String productId, String phone) {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(2);

        ObjectNode node = objectMapper.createObjectNode();
        node.set("items", objectMapper.valueToTree(List.of(item)));
        node.put("customerName", "Maria Souza");
        node.put("customerPhone", phone);
        node.put("street", "Rua das Flores");
        node.put("number", "123");
        node.put("neighborhood", "Centro");
        node.put("city", "Sao Paulo");
        return node;
    }

    @Test
    void createDeliveryOrder_withValidAddress_shouldOpenTablelessTabAndCreateOrder() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90");
        createDeliveryZone(token, "Centro", "8.00");

        MvcResult result = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/delivery/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deliveryOrderBody(productId, "11999990001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tabId").exists())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.deliveryFee").value(8.00))
                .andExpect(jsonPath("$.order.items", hasSize(1)))
                .andExpect(jsonPath("$.order.items[0].quantity").value(2))
                .andExpect(jsonPath("$.order.total").value(51.80))
                .andReturn();

        String tabId = JsonPath.read(result.getResponse().getContentAsString(), "$.tabId");
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/v1/tabs/" + tabId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables", hasSize(0)));

        UUID restaurantId = restaurantRepository.findBySlug(slug).orElseThrow().getId();
        TenantTestSupport.withTenant(restaurantId, () -> {
            DeliveryDetails details = deliveryDetailsRepository.findByTab_Id(UUID.fromString(tabId)).orElseThrow();
            Assertions.assertEquals("Maria Souza", details.getCustomerName());
            Assertions.assertEquals("Rua das Flores", details.getStreet());
            Assertions.assertEquals(accessToken, details.getAccessToken());
            Assertions.assertEquals(0, details.getDeliveryFee().compareTo(new BigDecimal("8.00")));
        });
    }

    @Test
    void createDeliveryOrder_eachCall_shouldOpenItsOwnTab() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90");
        createDeliveryZone(token, "Centro", "8.00");

        MvcResult first = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/delivery/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deliveryOrderBody(productId, "11999990002"))))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/delivery/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deliveryOrderBody(productId, "11999990003"))))
                .andExpect(status().isCreated())
                .andReturn();

        String firstTabId = JsonPath.read(first.getResponse().getContentAsString(), "$.tabId");
        String secondTabId = JsonPath.read(second.getResponse().getContentAsString(), "$.tabId");
        Assertions.assertNotEquals(firstTabId, secondTabId);
    }

    @Test
    void payTab_forDeliveryOrder_shouldIncludeDeliveryFeeInBillTotal() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90");
        createDeliveryZone(token, "Centro", "8.00");

        MvcResult created = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/delivery/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deliveryOrderBody(productId, "11999990007"))))
                .andExpect(status().isCreated())
                .andReturn();
        String tabId = JsonPath.read(created.getResponse().getContentAsString(), "$.tabId");
        String itemId = JsonPath.read(created.getResponse().getContentAsString(), "$.order.items[0].id");

        for (ItemStatus nextStatus : List.of(ItemStatus.PREPARING, ItemStatus.READY, ItemStatus.DELIVERED)) {
            UpdateOrderItemStatusRequest statusRequest = new UpdateOrderItemStatusRequest();
            statusRequest.setStatus(nextStatus);
            mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusRequest)))
                    .andExpect(status().isOk());
        }

        // Items total is 2 x 25.90 = 51.80; delivery fee is 8.00; service charge waived explicitly
        // so the math stays simple - billTotal should be exactly itemsTotal + deliveryFee.
        RegisterPaymentsRequest paymentRequest = new RegisterPaymentsRequest();
        PaymentEntryRequest entry = new PaymentEntryRequest();
        // PIX (a manual "customer paid by Pix" record, not the online gateway) - avoids needing an
        // open cash register session, which CASH payments require and this test doesn't set up.
        entry.setPaymentMethod(PaymentMethod.PIX);
        entry.setAmount(new BigDecimal("59.80"));
        paymentRequest.setPayments(List.of(entry));
        paymentRequest.setServiceChargePercentage(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.billTotal").value(59.80))
                .andExpect(jsonPath("$.remainingBalance").value(0));
    }

    @Test
    void createDeliveryOrder_forUnservedNeighborhood_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90");
        // Note: no delivery zone created for this restaurant at all.

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/delivery/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deliveryOrderBody(productId, "11999990006"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDeliveryOrder_missingStreet_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90");

        ObjectNode body = deliveryOrderBody(productId, "11999990004");
        body.remove("street");

        mockMvc.perform(post("/api/v1/public/menu/" + slug + "/delivery/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDeliveryOrder_unknownSlug_shouldReturn400() throws Exception {
        String token = registerOwnerAndGetToken();
        String slug = getSlug(token);
        String categoryId = createCategory(token, "Burgers");
        String productId = createProduct(token, categoryId, "Cheeseburger", "25.90");

        mockMvc.perform(post("/api/v1/public/menu/does-not-exist/delivery/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deliveryOrderBody(productId, "11999990005"))))
                .andExpect(status().isBadRequest());
    }
}
