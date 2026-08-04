package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.TableStatus;
import com.example.restaurant_saas.dto.response.DashboardResponse;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.PaymentRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<ItemStatus> OPEN_STATUSES = List.of(ItemStatus.PENDING, ItemStatus.PREPARING, ItemStatus.READY);

    private final RestaurantTableRepository tableRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID restaurantId) {
        OffsetDateTime startOfDay = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime startOfNextDay = startOfDay.plusDays(1);

        return DashboardResponse.builder()
                .freeTables(tableRepository.countByRestaurantIdAndStatus(restaurantId, TableStatus.FREE))
                .occupiedTables(tableRepository.countByRestaurantIdAndStatus(restaurantId, TableStatus.OCCUPIED))
                .ordersInPreparation(orderItemRepository.countByOrder_Restaurant_IdAndStatusIn(restaurantId, OPEN_STATUSES))
                .revenueToday(paymentRepository.sumNetActiveAmountByRestaurantIdAndPaidAtBetween(restaurantId, startOfDay, startOfNextDay))
                .build();
    }
}
