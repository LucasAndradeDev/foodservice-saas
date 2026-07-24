package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.dto.response.PaymentMethodTotalResponse;
import com.example.restaurant_saas.dto.response.ReportSummaryResponse;
import com.example.restaurant_saas.dto.response.TopProductResponse;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int TOP_PRODUCTS_LIMIT = 10;

    private final TabRepository tabRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public ReportSummaryResponse getSummary(UUID restaurantId, LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must not be after end date.");
        }

        OffsetDateTime rangeStart = start.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime rangeEnd = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();

        List<PaymentMethodTotalResponse> byPaymentMethod = tabRepository
                .sumPaidAmountByRestaurantIdAndPaidAtBetweenGroupedByPaymentMethod(restaurantId, rangeStart, rangeEnd)
                .stream()
                .map(row -> PaymentMethodTotalResponse.builder()
                        .paymentMethod((PaymentMethod) row[0])
                        .total((BigDecimal) row[1])
                        .tabsCount((Long) row[2])
                        .build())
                .toList();

        BigDecimal totalRevenue = byPaymentMethod.stream()
                .map(PaymentMethodTotalResponse::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long closedTabsCount = byPaymentMethod.stream()
                .mapToLong(PaymentMethodTotalResponse::getTabsCount)
                .sum();
        BigDecimal averageTicket = closedTabsCount == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(closedTabsCount), 2, RoundingMode.HALF_UP);

        List<TopProductResponse> topProducts = orderItemRepository
                .findTopSellingProducts(restaurantId, rangeStart, rangeEnd, PageRequest.of(0, TOP_PRODUCTS_LIMIT))
                .stream()
                .map(row -> TopProductResponse.builder()
                        .productId((UUID) row[0])
                        .productName((String) row[1])
                        .quantitySold((Long) row[2])
                        .revenue((BigDecimal) row[3])
                        .build())
                .toList();

        return ReportSummaryResponse.builder()
                .totalRevenue(totalRevenue)
                .closedTabsCount(closedTabsCount)
                .averageTicket(averageTicket)
                .byPaymentMethod(byPaymentMethod)
                .topProducts(topProducts)
                .build();
    }
}
