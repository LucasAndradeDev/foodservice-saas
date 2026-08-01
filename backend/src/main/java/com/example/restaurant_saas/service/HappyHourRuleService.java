package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Category;
import com.example.restaurant_saas.domain.entity.HappyHourRule;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.dto.request.HappyHourRuleRequest;
import com.example.restaurant_saas.dto.response.HappyHourRuleResponse;
import com.example.restaurant_saas.repository.CategoryRepository;
import com.example.restaurant_saas.repository.HappyHourRuleRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HappyHourRuleService {

    private final HappyHourRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<HappyHourRuleResponse> listRules(UUID restaurantId) {
        return ruleRepository.findByRestaurantIdOrderByCreatedAtAsc(restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HappyHourRuleResponse createRule(UUID restaurantId, HappyHourRuleRequest request) {
        validateRange(request.getStartTime(), request.getEndTime());
        validateDiscountValue(request.getDiscountType(), request.getDiscountValue());
        Category category = findCategory(restaurantId, request.getCategoryId());

        HappyHourRule rule = HappyHourRule.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .category(category)
                .daysOfWeek(request.getDaysOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .active(request.getActive())
                .build();

        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public HappyHourRuleResponse updateRule(UUID restaurantId, UUID ruleId, HappyHourRuleRequest request) {
        validateRange(request.getStartTime(), request.getEndTime());
        validateDiscountValue(request.getDiscountType(), request.getDiscountValue());
        HappyHourRule rule = findRule(restaurantId, ruleId);
        Category category = findCategory(restaurantId, request.getCategoryId());

        rule.setCategory(category);
        rule.setDaysOfWeek(request.getDaysOfWeek());
        rule.setStartTime(request.getStartTime());
        rule.setEndTime(request.getEndTime());
        rule.setDiscountType(request.getDiscountType());
        rule.setDiscountValue(request.getDiscountValue());
        rule.setActive(request.getActive());

        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(UUID restaurantId, UUID ruleId) {
        ruleRepository.delete(findRule(restaurantId, ruleId));
    }

    // Shared by MenuService and OrderService so "is happy hour active now" lives in one place.
    @Transactional(readOnly = true)
    public Map<UUID, HappyHourRule> fetchActiveRulesByCategory(UUID restaurantId) {
        ZonedDateTime now = OffsetDateTime.now().atZoneSameInstant(ZoneId.systemDefault());
        DayOfWeek today = now.getDayOfWeek();
        LocalTime timeNow = now.toLocalTime();

        return ruleRepository.findByRestaurantIdAndActiveTrue(restaurantId).stream()
                .filter(rule -> (rule.getDaysOfWeek() == null || rule.getDaysOfWeek().isEmpty() || rule.getDaysOfWeek().contains(today))
                        && !timeNow.isBefore(rule.getStartTime())
                        && !timeNow.isAfter(rule.getEndTime()))
                .collect(Collectors.toMap(rule -> rule.getCategory().getId(), rule -> rule, (a, b) -> a));
    }

    private void validateRange(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time.");
        }
    }

    private void validateDiscountValue(DiscountType type, BigDecimal value) {
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100.");
        }
    }

    private Category findCategory(UUID restaurantId, UUID categoryId) {
        return categoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));
    }

    private HappyHourRule findRule(UUID restaurantId, UUID ruleId) {
        return ruleRepository.findByIdAndRestaurantId(ruleId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Happy hour rule not found."));
    }

    private HappyHourRuleResponse toResponse(HappyHourRule rule) {
        List<DayOfWeek> sortedDays = rule.getDaysOfWeek() == null
                ? List.of()
                : rule.getDaysOfWeek().stream().sorted().toList();
        return HappyHourRuleResponse.builder()
                .id(rule.getId())
                .categoryId(rule.getCategory().getId())
                .categoryName(rule.getCategory().getName())
                .daysOfWeek(sortedDays)
                .startTime(rule.getStartTime())
                .endTime(rule.getEndTime())
                .discountType(rule.getDiscountType())
                .discountValue(rule.getDiscountValue())
                .active(rule.getActive())
                .build();
    }
}
