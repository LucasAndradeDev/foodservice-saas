package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Coupon;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.dto.request.CreateCouponRequest;
import com.example.restaurant_saas.dto.request.UpdateCouponRequest;
import com.example.restaurant_saas.dto.response.CouponResponse;
import com.example.restaurant_saas.repository.CouponRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<CouponResponse> listCoupons(UUID restaurantId) {
        return couponRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CouponResponse createCoupon(UUID restaurantId, CreateCouponRequest request) {
        if (couponRepository.existsByRestaurantIdAndCodeIgnoreCase(restaurantId, request.getCode())) {
            throw new IllegalArgumentException("A coupon with this code already exists.");
        }
        validateDiscountValue(request.getDiscountType(), request.getDiscountValue());
        if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Expiration date must be in the future.");
        }

        Coupon coupon = Coupon.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .code(request.getCode())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .expiresAt(request.getExpiresAt())
                .maxUses(request.getMaxUses())
                .build();

        return toResponse(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResponse updateCoupon(UUID restaurantId, UUID couponId, UpdateCouponRequest request) {
        Coupon coupon = findByIdAndRestaurant(restaurantId, couponId);

        if (request.getDiscountType() != null || request.getDiscountValue() != null) {
            DiscountType type = request.getDiscountType() != null ? request.getDiscountType() : coupon.getDiscountType();
            BigDecimal value = request.getDiscountValue() != null ? request.getDiscountValue() : coupon.getDiscountValue();
            validateDiscountValue(type, value);
            coupon.setDiscountType(type);
            coupon.setDiscountValue(value);
        }
        if (request.getActive() != null) {
            coupon.setActive(request.getActive());
        }

        if (Boolean.TRUE.equals(request.getClearExpiresAt())) {
            coupon.setExpiresAt(null);
        } else if (request.getExpiresAt() != null) {
            if (!request.getExpiresAt().isAfter(OffsetDateTime.now())) {
                throw new IllegalArgumentException("Expiration date must be in the future.");
            }
            coupon.setExpiresAt(request.getExpiresAt());
        }

        if (Boolean.TRUE.equals(request.getClearMaxUses())) {
            coupon.setMaxUses(null);
        } else if (request.getMaxUses() != null) {
            // Deliberately allowed even below usedCount: the existing atomic increment guard
            // (usedCount < maxUses) already makes the coupon stop working immediately in that
            // case — exactly what lowering the limit is meant to do, no extra check needed.
            coupon.setMaxUses(request.getMaxUses());
        }

        return toResponse(couponRepository.save(coupon));
    }

    private void validateDiscountValue(DiscountType type, BigDecimal value) {
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100.");
        }
    }

    private Coupon findByIdAndRestaurant(UUID restaurantId, UUID couponId) {
        return couponRepository.findByIdAndRestaurantId(couponId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found."));
    }

    private CouponResponse toResponse(Coupon coupon) {
        return CouponResponse.builder()
                .id(coupon.getId())
                .restaurantId(coupon.getRestaurant().getId())
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .active(coupon.getActive())
                .expiresAt(coupon.getExpiresAt())
                .maxUses(coupon.getMaxUses())
                .usedCount(coupon.getUsedCount())
                .build();
    }
}
