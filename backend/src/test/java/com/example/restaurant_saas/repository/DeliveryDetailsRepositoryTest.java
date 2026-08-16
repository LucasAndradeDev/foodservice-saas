package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import com.example.restaurant_saas.dto.request.CreateCategoryRequest;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.dto.request.CreateProductRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.support.TenantTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the {@code @Filter(name = "tenantFilter", ...)} on {@link DeliveryDetails} (Camada 2,
 * see project_rls_design) actually blocks a cross-tenant read - restaurant B's tenant context must
 * never see restaurant A's delivery address. There's no authenticated by-id endpoint for
 * DeliveryDetails yet (that lands with the staff-facing delivery list in task 27.2, where a
 * CrossTenantIsolationControllerIntegrationTest case belongs too), so this checks the repository
 * directly instead - same spirit as ReservationRepositoryTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeliveryDetailsRepositoryTest {

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

    @Test
    void findByTabId_underAnotherTenant_returnsEmpty() throws Exception {
        String tokenA = registerOwnerAndGetToken();
        String slugA = getSlug(tokenA);
        String categoryIdA = createCategory(tokenA, "Burgers");
        String productIdA = createProduct(tokenA, categoryIdA, "Cheeseburger", "25.90");
        String tabId = createDeliveryOrder(slugA, productIdA);
        UUID restaurantIdA = restaurantRepository.findBySlug(slugA).orElseThrow().getId();

        registerRequest = new RegisterRestaurantRequest();
        registerRequest.setRestaurantName("Pizza Place");
        registerRequest.setOwnerName("Owner B");
        registerRequest.setOwnerEmail("owner-b+" + System.nanoTime() + "@test.com");
        registerRequest.setOwnerPassword("password123");
        String tokenB = registerOwnerAndGetToken();
        String slugB = getSlug(tokenB);
        UUID restaurantIdB = restaurantRepository.findBySlug(slugB).orElseThrow().getId();

        Optional<DeliveryDetails> asOwnTenant = TenantTestSupport.withTenant(restaurantIdA,
                () -> deliveryDetailsRepository.findByTab_Id(UUID.fromString(tabId)));
        Optional<DeliveryDetails> asOtherTenant = TenantTestSupport.withTenant(restaurantIdB,
                () -> deliveryDetailsRepository.findByTab_Id(UUID.fromString(tabId)));

        assertThat(asOwnTenant).isPresent();
        assertThat(asOtherTenant).isEmpty();
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

    private String createDeliveryOrder(String slug, String productId) throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.fromString(productId));
        item.setQuantity(1);

        ObjectNode node = objectMapper.createObjectNode();
        node.set("items", objectMapper.valueToTree(List.of(item)));
        node.put("customerName", "Maria Souza");
        node.put("customerPhone", "11999990009");
        node.put("street", "Rua das Flores");
        node.put("number", "123");
        node.put("neighborhood", "Centro");
        node.put("city", "Sao Paulo");

        MvcResult result = mockMvc.perform(post("/api/v1/public/menu/" + slug + "/delivery/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(node)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.tabId");
    }
}
