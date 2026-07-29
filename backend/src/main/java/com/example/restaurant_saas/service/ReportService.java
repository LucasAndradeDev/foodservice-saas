package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.dto.response.PaymentMethodTotalResponse;
import com.example.restaurant_saas.dto.response.PeakHourCellResponse;
import com.example.restaurant_saas.dto.response.PeakHoursResponse;
import com.example.restaurant_saas.dto.response.ReportComparisonResponse;
import com.example.restaurant_saas.dto.response.ReportSummaryResponse;
import com.example.restaurant_saas.dto.response.TopProductResponse;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int TOP_PRODUCTS_LIMIT = 10;

    private final TabRepository tabRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;

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
                .map(row -> {
                    long costQuantityCovered = (Long) row[4];
                    BigDecimal costTotal = (BigDecimal) row[5];
                    BigDecimal revenueCovered = (BigDecimal) row[6];
                    BigDecimal marginTotal = revenueCovered.subtract(costTotal);
                    BigDecimal marginPercentage = costQuantityCovered > 0 && revenueCovered.compareTo(BigDecimal.ZERO) > 0
                            ? marginTotal.multiply(BigDecimal.valueOf(100)).divide(revenueCovered, 1, RoundingMode.HALF_UP)
                            : null;

                    return TopProductResponse.builder()
                            .productId((UUID) row[0])
                            .productName((String) row[1])
                            .quantitySold((Long) row[2])
                            .revenue((BigDecimal) row[3])
                            .costQuantityCovered(costQuantityCovered)
                            .costTotal(costTotal)
                            .marginTotal(marginTotal)
                            .marginPercentage(marginPercentage)
                            .build();
                })
                .toList();

        ReportComparisonResponse comparison = getComparison(restaurantId, start, end, totalRevenue, closedTabsCount, averageTicket);

        return ReportSummaryResponse.builder()
                .totalRevenue(totalRevenue)
                .closedTabsCount(closedTabsCount)
                .averageTicket(averageTicket)
                .byPaymentMethod(byPaymentMethod)
                .topProducts(topProducts)
                .comparison(comparison)
                .build();
    }

    /**
     * Previous period = the same number of days immediately before the selected range,
     * regardless of calendar month boundaries — always an equal-length, apples-to-apples
     * comparison (e.g. comparing all of March to Feb 28 alone would inflate March's growth
     * just because March has 3 more days, so the window borrows those days from January).
     */
    private ReportComparisonResponse getComparison(UUID restaurantId, LocalDate start, LocalDate end,
                                                     BigDecimal totalRevenue, long closedTabsCount, BigDecimal averageTicket) {
        long periodDays = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(periodDays - 1);

        OffsetDateTime previousRangeStart = previousStart.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime previousRangeEnd = previousEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();

        List<PaymentMethodTotalResponse> previousByPaymentMethod = tabRepository
                .sumPaidAmountByRestaurantIdAndPaidAtBetweenGroupedByPaymentMethod(restaurantId, previousRangeStart, previousRangeEnd)
                .stream()
                .map(row -> PaymentMethodTotalResponse.builder()
                        .paymentMethod((PaymentMethod) row[0])
                        .total((BigDecimal) row[1])
                        .tabsCount((Long) row[2])
                        .build())
                .toList();

        BigDecimal previousTotalRevenue = previousByPaymentMethod.stream()
                .map(PaymentMethodTotalResponse::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long previousClosedTabsCount = previousByPaymentMethod.stream()
                .mapToLong(PaymentMethodTotalResponse::getTabsCount)
                .sum();
        BigDecimal previousAverageTicket = previousClosedTabsCount == 0
                ? BigDecimal.ZERO
                : previousTotalRevenue.divide(BigDecimal.valueOf(previousClosedTabsCount), 2, RoundingMode.HALF_UP);

        return ReportComparisonResponse.builder()
                .previousTotalRevenue(previousTotalRevenue)
                .previousClosedTabsCount(previousClosedTabsCount)
                .previousAverageTicket(previousAverageTicket)
                .revenueChangePercentage(percentageChange(totalRevenue, previousTotalRevenue))
                .closedTabsChangePercentage(percentageChange(BigDecimal.valueOf(closedTabsCount), BigDecimal.valueOf(previousClosedTabsCount)))
                .averageTicketChangePercentage(percentageChange(averageTicket, previousAverageTicket))
                .build();
    }

    private BigDecimal percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public PeakHoursResponse getPeakHours(UUID restaurantId, LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must not be after end date.");
        }

        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime rangeStart = start.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime rangeEnd = end.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        Map<DayOfWeek, Integer> occurrencesByWeekday = new EnumMap<>(DayOfWeek.class);
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            occurrencesByWeekday.merge(date.getDayOfWeek(), 1, Integer::sum);
        }

        Map<DayOfWeek, Map<Integer, Long>> occupiedTableHours = new EnumMap<>(DayOfWeek.class);
        for (Tab tab : tabRepository.findClosedTabsWithTablesOverlappingRange(restaurantId, rangeStart, rangeEnd)) {
            long tableCount = tab.getTables().size();
            OffsetDateTime overlapStart = tab.getOpenedAt().isBefore(rangeStart) ? rangeStart : tab.getOpenedAt();
            OffsetDateTime overlapEnd = tab.getClosedAt().isAfter(rangeEnd) ? rangeEnd : tab.getClosedAt();

            ZonedDateTime hourCursor = overlapStart.atZoneSameInstant(zone).truncatedTo(ChronoUnit.HOURS);
            ZonedDateTime overlapEndZoned = overlapEnd.atZoneSameInstant(zone);
            while (hourCursor.isBefore(overlapEndZoned)) {
                occupiedTableHours
                        .computeIfAbsent(hourCursor.getDayOfWeek(), k -> new HashMap<>())
                        .merge(hourCursor.getHour(), tableCount, Long::sum);
                hourCursor = hourCursor.plusHours(1);
            }
        }

        Map<DayOfWeek, Map<Integer, Long>> orderCounts = new EnumMap<>(DayOfWeek.class);
        for (OffsetDateTime createdAt : orderRepository.findCreatedAtByRestaurantIdAndCreatedAtBetween(restaurantId, rangeStart, rangeEnd)) {
            ZonedDateTime zoned = createdAt.atZoneSameInstant(zone);
            orderCounts
                    .computeIfAbsent(zoned.getDayOfWeek(), k -> new HashMap<>())
                    .merge(zoned.getHour(), 1L, Long::sum);
        }

        List<PeakHourCellResponse> cells = new ArrayList<>();
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            int occurrences = occurrencesByWeekday.getOrDefault(dayOfWeek, 0);
            for (int hour = 0; hour < 24; hour++) {
                long occupiedTableHourCount = occupiedTableHours
                        .getOrDefault(dayOfWeek, Map.of())
                        .getOrDefault(hour, 0L);
                long orderCount = orderCounts
                        .getOrDefault(dayOfWeek, Map.of())
                        .getOrDefault(hour, 0L);

                cells.add(PeakHourCellResponse.builder()
                        .dayOfWeek(dayOfWeek)
                        .hour(hour)
                        .avgOccupiedTables(average(occupiedTableHourCount, occurrences))
                        .avgOrderCount(average(orderCount, occurrences))
                        .sampleCount(occurrences)
                        .build());
            }
        }

        return PeakHoursResponse.builder().cells(cells).build();
    }

    private BigDecimal average(long total, int occurrences) {
        if (occurrences == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(occurrences), 1, RoundingMode.HALF_UP);
    }
}
