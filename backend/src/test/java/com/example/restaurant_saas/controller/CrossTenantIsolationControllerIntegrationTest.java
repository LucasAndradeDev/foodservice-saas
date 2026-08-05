package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.ModifierSelectionType;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.domain.enums.TableRequestType;
import com.example.restaurant_saas.domain.enums.TableStatus;
import com.example.restaurant_saas.dto.request.ApplyDiscountRequest;
import com.example.restaurant_saas.dto.request.AvailabilityWindowRequest;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateCouponRequest;
import com.example.restaurant_saas.dto.request.CreateDiningAreaRequest;
import com.example.restaurant_saas.dto.request.CreateModifierGroupRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateOrderRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.CreateReservationRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequest;
import com.example.restaurant_saas.dto.request.CreateTableRequestRequest;
import com.example.restaurant_saas.dto.request.HappyHourRuleRequest;
import com.example.restaurant_saas.dto.request.ModifierOptionInput;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PaymentEntryRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.UpdateCategoryRequest;
import com.example.restaurant_saas.dto.request.UpdateCouponRequest;
import com.example.restaurant_saas.dto.request.UpdateDiningAreaRequest;
import com.example.restaurant_saas.dto.request.UpdateModifierGroupRequest;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.dto.request.UpdateProductRequest;
import com.example.restaurant_saas.dto.request.UpdateTableRequest;
import com.example.restaurant_saas.dto.request.UpdateTableStatusRequest;
import com.example.restaurant_saas.dto.request.UpdateUserRequest;
import com.example.restaurant_saas.dto.request.VoidPaymentRequest;
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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Systematic sweep of every id-based endpoint: create a resource under restaurant A, then hit it
 * with restaurant B's token and assert the request is rejected (never a 200/2xx). Scoping today is
 * manual (each service call takes restaurantId and must remember to filter by it) — this suite is
 * the regression net for that, not a substitute for the Hibernate-filter/RLS hardening discussed
 * separately.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CrossTenantIsolationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        tokenA = registerRestaurantAndGetToken("Burger House A");
        tokenB = registerRestaurantAndGetToken("Pizza Place B");
    }

    // ---------- setup helpers ----------

    private String registerRestaurantAndGetToken(String name) throws Exception {
        RegisterRestaurantRequest request = new RegisterRestaurantRequest();
        request.setRestaurantName(name);
        request.setPhone("11999999999");
        request.setAddress("Main St, 100");
        request.setOwnerName("Owner");
        request.setOwnerEmail("owner+" + UUID.randomUUID() + "@test.com");
        request.setOwnerPassword("password123");

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

    private String getOwnUserId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$[0].id");
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

    private String createProduct(String token, String categoryId, String name) throws Exception {
        CreateProductRequest request = new CreateProductRequest();
        request.setName(name);
        request.setPrice(new BigDecimal("25.90"));
        request.setCategoryId(UUID.fromString(categoryId));
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTable(String token) throws Exception {
        CreateTableRequest request = new CreateTableRequest();
        request.setCapacity(4);
        MvcResult result = mockMvc.perform(post("/api/v1/tables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String openTab(String token, String tableId) throws Exception {
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

    private String createOrderAndGetId(String token, String tabId, String productId) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        MvcResult result = mockMvc.perform(post("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String createDiningArea(String token, String name) throws Exception {
        CreateDiningAreaRequest request = new CreateDiningAreaRequest();
        request.setName(name);
        MvcResult result = mockMvc.perform(post("/api/v1/dining-areas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createCoupon(String token, String code) throws Exception {
        CreateCouponRequest request = new CreateCouponRequest();
        request.setCode(code);
        request.setDiscountType(DiscountType.PERCENTAGE);
        request.setDiscountValue(new BigDecimal("10"));
        MvcResult result = mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createHappyHourRule(String token, String categoryId) throws Exception {
        HappyHourRuleRequest request = new HappyHourRuleRequest();
        request.setCategoryId(UUID.fromString(categoryId));
        request.setDaysOfWeek(Set.of(DayOfWeek.FRIDAY));
        request.setStartTime(LocalTime.of(17, 0));
        request.setEndTime(LocalTime.of(19, 0));
        request.setDiscountType(DiscountType.PERCENTAGE);
        request.setDiscountValue(new BigDecimal("20"));
        request.setActive(true);
        MvcResult result = mockMvc.perform(post("/api/v1/happy-hour-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createReservation(String token, String tableId) throws Exception {
        CreateReservationRequest request = new CreateReservationRequest();
        request.setCustomerName("Jane Doe");
        request.setCustomerPhone("11988887777");
        request.setPartySize(2);
        request.setReservationTime(OffsetDateTime.now().plusDays(1));
        request.setTableIds(List.of(UUID.fromString(tableId)));
        MvcResult result = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createModifierGroup(String token, String productId) throws Exception {
        CreateModifierGroupRequest request = new CreateModifierGroupRequest();
        request.setName("Size");
        request.setSelectionType(ModifierSelectionType.SINGLE);
        request.setRequired(true);
        ModifierOptionInput option = new ModifierOptionInput();
        option.setName("Large");
        option.setPriceDelta(new BigDecimal("5.00"));
        request.setOptions(List.of(option));
        MvcResult result = mockMvc.perform(post("/api/v1/products/" + productId + "/modifier-groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createAvailabilityWindow(String token, String productId) throws Exception {
        AvailabilityWindowRequest request = new AvailabilityWindowRequest();
        request.setStartTime(LocalTime.of(11, 0));
        request.setEndTime(LocalTime.of(15, 0));
        MvcResult result = mockMvc.perform(post("/api/v1/products/" + productId + "/availability-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTableRequest(String slug, String tableId) throws Exception {
        CreateTableRequestRequest request = new CreateTableRequestRequest();
        request.setType(TableRequestType.CALL_WAITER);
        MvcResult result = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/tables/" + tableId + "/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    // ---------- cross-tenant tests ----------

    @Test
    void category_crossTenant_getAndUpdate_shouldBeRejected() throws Exception {
        String categoryId = createCategory(tokenA, "Burgers");

        mockMvc.perform(get("/api/v1/categories/" + categoryId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());

        UpdateCategoryRequest update = new UpdateCategoryRequest();
        update.setName("Hacked");
        mockMvc.perform(put("/api/v1/categories/" + categoryId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void product_crossTenant_getUpdateDelete_shouldBeRejected() throws Exception {
        String categoryId = createCategory(tokenA, "Burgers");
        String productId = createProduct(tokenA, categoryId, "Cheeseburger");

        mockMvc.perform(get("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());

        UpdateProductRequest update = new UpdateProductRequest();
        update.setName("Hacked");
        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void table_crossTenant_getUpdateDeleteStatus_shouldBeRejected() throws Exception {
        String tableId = createTable(tokenA);

        mockMvc.perform(get("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());

        UpdateTableRequest update = new UpdateTableRequest();
        update.setCapacity(6);
        mockMvc.perform(put("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());

        UpdateTableStatusRequest statusUpdate = new UpdateTableStatusRequest();
        statusUpdate.setStatus(TableStatus.OCCUPIED);
        mockMvc.perform(patch("/api/v1/tables/" + tableId + "/status")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusUpdate)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/tables/" + tableId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tab_crossTenant_allOperations_shouldBeRejected() throws Exception {
        String tableId = createTable(tokenA);
        String tabId = openTab(tokenA, tableId);

        mockMvc.perform(get("/api/v1/tabs/" + tabId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());

        ApplyDiscountRequest discount = new ApplyDiscountRequest();
        discount.setDiscountType(DiscountType.FIXED);
        discount.setDiscountValue(new BigDecimal("5.00"));
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/discount")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(discount)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/print")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());

        RegisterPaymentsRequest payment = new RegisterPaymentsRequest();
        PaymentEntryRequest entry = new PaymentEntryRequest();
        entry.setPaymentMethod(PaymentMethod.CASH);
        entry.setAmount(new BigDecimal("10.00"));
        payment.setPayments(List.of(entry));
        mockMvc.perform(post("/api/v1/tabs/" + tabId + "/payments")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isBadRequest());

        VoidPaymentRequest voidRequest = new VoidPaymentRequest();
        voidRequest.setReason("test");
        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/payments/" + UUID.randomUUID() + "/void")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(voidRequest)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/tabs/" + tabId + "/cancel")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void order_crossTenant_getAndListAndPrint_shouldBeRejected() throws Exception {
        String categoryId = createCategory(tokenA, "Burgers");
        String productId = createProduct(tokenA, categoryId, "Cheeseburger");
        String tableId = createTable(tokenA);
        String tabId = openTab(tokenA, tableId);
        String orderJson = createOrderAndGetId(tokenA, tabId, productId);
        String orderId = JsonPath.read(orderJson, "$.id");

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());

        // List endpoint filters by tabId AND restaurantId in the query itself (OrderService#listOrders),
        // so a cross-tenant tabId never leaks data — it just comes back empty instead of a 400, unlike
        // the singular get-by-id endpoints. No isolation gap here, just a different (still safe) shape.
        mockMvc.perform(get("/api/v1/tabs/" + tabId + "/orders")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(0));

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/print")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orderItem_crossTenant_statusAndDiscount_shouldBeRejected() throws Exception {
        String categoryId = createCategory(tokenA, "Burgers");
        String productId = createProduct(tokenA, categoryId, "Cheeseburger");
        String tableId = createTable(tokenA);
        String tabId = openTab(tokenA, tableId);
        String orderJson = createOrderAndGetId(tokenA, tabId, productId);
        String itemId = JsonPath.read(orderJson, "$.items[0].id");

        UpdateOrderItemStatusRequest statusUpdate = new UpdateOrderItemStatusRequest();
        statusUpdate.setStatus(com.example.restaurant_saas.domain.enums.ItemStatus.PREPARING);
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusUpdate)))
                .andExpect(status().isBadRequest());

        ApplyDiscountRequest discount = new ApplyDiscountRequest();
        discount.setDiscountType(DiscountType.FIXED);
        discount.setDiscountValue(new BigDecimal("2.00"));
        mockMvc.perform(patch("/api/v1/order-items/" + itemId + "/discount")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(discount)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coupon_crossTenant_updateAndDelete_shouldBeRejected() throws Exception {
        String couponId = createCoupon(tokenA, "SAVE10");

        UpdateCouponRequest update = new UpdateCouponRequest();
        update.setActive(false);
        mockMvc.perform(put("/api/v1/coupons/" + couponId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/coupons/" + couponId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void diningArea_crossTenant_updateAndDelete_shouldBeRejected() throws Exception {
        String areaId = createDiningArea(tokenA, "Main hall");

        UpdateDiningAreaRequest update = new UpdateDiningAreaRequest();
        update.setName("Hacked");
        mockMvc.perform(put("/api/v1/dining-areas/" + areaId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/dining-areas/" + areaId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void happyHourRule_crossTenant_updateAndDelete_shouldBeRejected() throws Exception {
        String categoryId = createCategory(tokenA, "Burgers");
        String ruleId = createHappyHourRule(tokenA, categoryId);

        HappyHourRuleRequest update = new HappyHourRuleRequest();
        update.setCategoryId(UUID.fromString(categoryId));
        update.setDaysOfWeek(Set.of(DayOfWeek.SATURDAY));
        update.setStartTime(LocalTime.of(18, 0));
        update.setEndTime(LocalTime.of(20, 0));
        update.setDiscountType(DiscountType.PERCENTAGE);
        update.setDiscountValue(new BigDecimal("30"));
        update.setActive(true);
        mockMvc.perform(put("/api/v1/happy-hour-rules/" + ruleId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/happy-hour-rules/" + ruleId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservation_crossTenant_checkInAndCancel_shouldBeRejected() throws Exception {
        String tableId = createTable(tokenA);
        String reservationId = createReservation(tokenA, tableId);

        mockMvc.perform(patch("/api/v1/reservations/" + reservationId + "/check-in")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/reservations/" + reservationId + "/cancel")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void user_crossTenant_getAndUpdate_shouldBeRejected() throws Exception {
        String ownerAId = getOwnUserId(tokenA);

        mockMvc.perform(get("/api/v1/users/" + ownerAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());

        UpdateUserRequest update = new UpdateUserRequest();
        update.setName("Hacked");
        mockMvc.perform(put("/api/v1/users/" + ownerAId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modifierGroup_crossTenant_updateAndDelete_shouldBeRejected() throws Exception {
        String categoryId = createCategory(tokenA, "Burgers");
        String productId = createProduct(tokenA, categoryId, "Cheeseburger");
        String groupId = createModifierGroup(tokenA, productId);

        UpdateModifierGroupRequest update = new UpdateModifierGroupRequest();
        update.setName("Hacked");
        mockMvc.perform(put("/api/v1/products/" + productId + "/modifier-groups/" + groupId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/products/" + productId + "/modifier-groups/" + groupId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void availabilityWindow_crossTenant_updateAndDelete_shouldBeRejected() throws Exception {
        String categoryId = createCategory(tokenA, "Burgers");
        String productId = createProduct(tokenA, categoryId, "Cheeseburger");
        String windowId = createAvailabilityWindow(tokenA, productId);

        AvailabilityWindowRequest update = new AvailabilityWindowRequest();
        update.setStartTime(LocalTime.of(12, 0));
        update.setEndTime(LocalTime.of(14, 0));
        mockMvc.perform(put("/api/v1/products/" + productId + "/availability-windows/" + windowId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/products/" + productId + "/availability-windows/" + windowId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tableRequest_crossTenant_acknowledge_shouldBeRejected() throws Exception {
        String slugA = getSlug(tokenA);
        String tableId = createTable(tokenA);
        String requestId = createTableRequest(slugA, tableId);

        mockMvc.perform(patch("/api/v1/table-requests/" + requestId + "/acknowledge")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isBadRequest());
    }
}
