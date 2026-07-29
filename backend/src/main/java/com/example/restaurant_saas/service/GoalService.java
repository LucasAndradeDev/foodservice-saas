package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.MonthlyGoal;
import com.example.restaurant_saas.dto.request.SetMonthlyGoalRequest;
import com.example.restaurant_saas.dto.response.MonthlyGoalResponse;
import com.example.restaurant_saas.repository.MonthlyGoalRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final MonthlyGoalRepository monthlyGoalRepository;
    private final TabRepository tabRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public MonthlyGoalResponse getMonthlyGoal(UUID restaurantId, LocalDate month) {
        LocalDate normalizedMonth = month.withDayOfMonth(1);

        BigDecimal revenueGoal = monthlyGoalRepository.findByRestaurantIdAndMonth(restaurantId, normalizedMonth)
                .map(MonthlyGoal::getRevenueGoal)
                .orElse(null);

        BigDecimal currentRevenue = getCurrentRevenue(restaurantId, normalizedMonth);

        BigDecimal progressPercentage = revenueGoal != null
                ? currentRevenue.divide(revenueGoal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                : null;

        return MonthlyGoalResponse.builder()
                .month(normalizedMonth)
                .revenueGoal(revenueGoal)
                .currentRevenue(currentRevenue)
                .progressPercentage(progressPercentage)
                .build();
    }

    @Transactional
    public MonthlyGoalResponse setMonthlyGoal(UUID restaurantId, SetMonthlyGoalRequest request) {
        LocalDate normalizedMonth = request.getMonth().withDayOfMonth(1);

        MonthlyGoal goal = monthlyGoalRepository.findByRestaurantIdAndMonth(restaurantId, normalizedMonth)
                .orElseGet(() -> MonthlyGoal.builder()
                        .restaurant(restaurantRepository.getReferenceById(restaurantId))
                        .month(normalizedMonth)
                        .build());
        goal.setRevenueGoal(request.getRevenueGoal());
        monthlyGoalRepository.save(goal);

        return getMonthlyGoal(restaurantId, normalizedMonth);
    }

    private BigDecimal getCurrentRevenue(UUID restaurantId, LocalDate normalizedMonth) {
        LocalDate monthEnd = normalizedMonth.plusMonths(1).minusDays(1);
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime rangeStart = normalizedMonth.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime rangeEnd = monthEnd.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        return tabRepository.sumPaidAmountByRestaurantIdAndPaidAtBetween(restaurantId, rangeStart, rangeEnd);
    }
}
