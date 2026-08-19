package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.UpdateOrderItemStatusRequest;
import com.example.restaurant_saas.repository.DeliveryDetailsRepository;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.TabRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the "your order is ready" WhatsApp notification triggered from
 * {@link OrderItemService#updateStatus}, i.e. the {@code notifyIfOrderReady} guard.
 */
@ExtendWith(MockitoExtension.class)
class OrderItemServiceTest {

    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TabRepository tabRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private DeliveryDetailsRepository deliveryDetailsRepository;
    @Mock
    private WhatsAppService whatsAppService;

    private OrderItemService orderItemService;

    private Restaurant restaurant;
    private Tab tab;
    private Order order;
    private OrderItem item;

    @BeforeEach
    void setUp() {
        orderItemService = new OrderItemService(orderItemRepository, orderRepository, tabRepository, restaurantRepository, deliveryDetailsRepository, whatsAppService);
        // None of these tests are about a delivery order - every tab here is a plain dine-in tab.
        lenient().when(deliveryDetailsRepository.findByTab_Id(any())).thenReturn(Optional.empty());

        restaurant = Restaurant.builder()
                .id(UUID.randomUUID())
                .name("Point Burger LTDA")
                .tradeName("Point Burger")
                .build();

        tab = Tab.builder()
                .id(UUID.randomUUID())
                .restaurant(restaurant)
                .build();

        order = Order.builder()
                .id(UUID.randomUUID())
                .restaurant(restaurant)
                .tab(tab)
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("X-Burger")
                .build();

        item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .product(product)
                .quantity(1)
                .unitPrice(BigDecimal.TEN)
                .status(ItemStatus.PREPARING)
                .build();
    }

    private void stubFind(OrderItem toReturn) {
        when(orderItemRepository.findByIdAndOrder_Restaurant_Id(eq(item.getId()), eq(restaurant.getId())))
                .thenReturn(Optional.of(toReturn));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void markReady() {
        UpdateOrderItemStatusRequest request = new UpdateOrderItemStatusRequest();
        request.setStatus(ItemStatus.READY);
        orderItemService.updateStatus(restaurant.getId(), item.getId(), UserRole.MANAGER, "Ana", request);
    }

    @Test
    void whenTabHasPhoneAndNoOtherPendingItems_sendsNotificationOnce() {
        tab.setCustomerPhone("11999999999");
        stubFind(item);
        when(orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(eq(tab.getId()), anyList())).thenReturn(false);

        markReady();

        verify(whatsAppService, times(1)).sendOrderReadyNotification("11999999999", "Point Burger");
        assertThat(tab.getReadyNotificationSentAt()).isNotNull();
        verify(tabRepository, times(1)).save(tab);
    }

    @Test
    void whenOtherItemsStillPending_doesNotSendNotification() {
        tab.setCustomerPhone("11999999999");
        stubFind(item);
        when(orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(eq(tab.getId()), anyList())).thenReturn(true);

        markReady();

        verifyNoInteractions(whatsAppService);
        assertThat(tab.getReadyNotificationSentAt()).isNull();
        verify(tabRepository, never()).save(any());
    }

    @Test
    void whenTabHasNoCustomerPhone_doesNotSendNotification() {
        tab.setCustomerPhone(null);
        stubFind(item);

        markReady();

        verifyNoInteractions(whatsAppService);
        verify(orderItemRepository, never()).existsByOrder_Tab_IdAndStatusNotIn(any(), anyList());
        verify(tabRepository, never()).save(any());
    }

    @Test
    void whenAlreadyNotified_doesNotSendNotificationAgain() {
        tab.setCustomerPhone("11999999999");
        tab.setReadyNotificationSentAt(OffsetDateTime.now().minusMinutes(5));
        stubFind(item);

        markReady();

        verifyNoInteractions(whatsAppService);
        verify(orderItemRepository, never()).existsByOrder_Tab_IdAndStatusNotIn(any(), anyList());
        verify(tabRepository, never()).save(any());
    }

    @Test
    void whenTransitionTargetIsNotReady_neverChecksNotification() {
        tab.setCustomerPhone("11999999999");
        item.setStatus(ItemStatus.PENDING);
        stubFind(item);

        UpdateOrderItemStatusRequest request = new UpdateOrderItemStatusRequest();
        request.setStatus(ItemStatus.PREPARING);
        orderItemService.updateStatus(restaurant.getId(), item.getId(), UserRole.KITCHEN, "Ana", request);

        verifyNoInteractions(whatsAppService);
        verify(orderItemRepository, never()).existsByOrder_Tab_IdAndStatusNotIn(any(), anyList());
    }

    @Test
    void whenTransitionIsCancellation_neverChecksNotification() {
        tab.setCustomerPhone("11999999999");
        stubFind(item);

        UpdateOrderItemStatusRequest request = new UpdateOrderItemStatusRequest();
        request.setStatus(ItemStatus.CANCELLED);
        orderItemService.updateStatus(restaurant.getId(), item.getId(), UserRole.WAITER, "Ana", request);

        verifyNoInteractions(whatsAppService);
        verify(orderItemRepository, never()).existsByOrder_Tab_IdAndStatusNotIn(any(), anyList());
    }

    @Test
    void whenTransitionIsDelivery_neverChecksNotificationAgain() {
        tab.setCustomerPhone("11999999999");
        item.setStatus(ItemStatus.READY);
        stubFind(item);

        UpdateOrderItemStatusRequest request = new UpdateOrderItemStatusRequest();
        request.setStatus(ItemStatus.DELIVERED);
        orderItemService.updateStatus(restaurant.getId(), item.getId(), UserRole.WAITER, "Ana", request);

        verifyNoInteractions(whatsAppService);
        verify(orderItemRepository, never()).existsByOrder_Tab_IdAndStatusNotIn(any(), anyList());
    }

    @Test
    void whenRestaurantHasNoTradeName_fallsBackToLegalName() {
        restaurant.setTradeName(null);
        tab.setCustomerPhone("11999999999");
        stubFind(item);
        when(orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(eq(tab.getId()), anyList())).thenReturn(false);

        markReady();

        verify(whatsAppService).sendOrderReadyNotification("11999999999", "Point Burger LTDA");
    }

    @Test
    void excludesReadyDeliveredAndCancelled_whenCheckingForPendingSiblingItems() {
        tab.setCustomerPhone("11999999999");
        stubFind(item);
        when(orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(eq(tab.getId()), anyList())).thenReturn(false);

        markReady();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).existsByOrder_Tab_IdAndStatusNotIn(eq(tab.getId()), statusesCaptor.capture());
        assertThat(statusesCaptor.getValue())
                .containsExactlyInAnyOrder(ItemStatus.READY, ItemStatus.DELIVERED, ItemStatus.CANCELLED);
    }

    @Test
    void comboHeaderReady_updatesChildrenAndNotifiesOnlyOnce() {
        tab.setCustomerPhone("11999999999");

        OrderItem child = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .product(item.getProduct())
                .quantity(1)
                .unitPrice(BigDecimal.ONE)
                .status(ItemStatus.PREPARING)
                .parentOrderItem(item)
                .build();
        item.setChildren(List.of(child));

        stubFind(item);
        when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(eq(tab.getId()), anyList())).thenReturn(false);

        markReady();

        assertThat(child.getStatus()).isEqualTo(ItemStatus.READY);
        verify(whatsAppService, times(1)).sendOrderReadyNotification(any(), any());
    }
}
