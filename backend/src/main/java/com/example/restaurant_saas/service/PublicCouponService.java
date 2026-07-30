package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Coupon;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.dto.request.RedeemCouponRequest;
import com.example.restaurant_saas.dto.response.PublicCouponRedemptionResponse;
import com.example.restaurant_saas.repository.CouponRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.TabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicCouponService {

    private final RestaurantRepository restaurantRepository;
    private final CouponRepository couponRepository;
    private final TabRepository tabRepository;
    private final TabService tabService;

    @Transactional
    public PublicCouponRedemptionResponse redeem(String slug, UUID tableId, RedeemCouponRequest request) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        Coupon coupon = couponRepository.findByRestaurantIdAndCodeIgnoreCase(restaurant.getId(), request.getCode())
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found or inactive."));
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("This coupon has expired.");
        }

        Tab tab = tabService.openOrGetTabForTable(restaurant.getId(), tableId);
        String reason = "Cupom: " + coupon.getCode();

        if (!reason.equalsIgnoreCase(tab.getDiscountReason())) {
            if (couponRepository.incrementUsage(coupon.getId()) == 0) {
                throw new IllegalStateException("This coupon has already reached its usage limit.");
            }
            tab.setDiscountType(coupon.getDiscountType());
            tab.setDiscountValue(coupon.getDiscountValue());
            tab.setDiscountReason(reason);
            tab.setDiscountAppliedBy("Cliente (autoatendimento)");
            tab.setDiscountAppliedAt(OffsetDateTime.now());
            tab = tabRepository.save(tab);
        }

        return PublicCouponRedemptionResponse.builder()
                .discountAppliedLabel(buildDiscountLabel(tab))
                .build();
    }

    static String buildDiscountLabel(Tab tab) {
        if (tab.getDiscountType() == null || tab.getDiscountValue() == null) {
            return null;
        }
        String amount = tab.getDiscountType() == DiscountType.PERCENTAGE
                ? tab.getDiscountValue().stripTrailingZeros().toPlainString() + "%"
                : "R$ " + tab.getDiscountValue();
        return tab.getDiscountReason() != null ? tab.getDiscountReason() + " · -" + amount : "-" + amount;
    }
}
