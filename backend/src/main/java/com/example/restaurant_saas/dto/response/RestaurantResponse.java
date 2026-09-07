package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class RestaurantResponse {
    private UUID id;
    private String name;
    private String tradeName;
    private String slug;
    private String cnpj;
    private String phone;
    private String address;
    private String street;
    private String number;
    private String complement;
    private String neighborhood;
    private String city;
    private String zipCode;
    // Geocoded from address above (task 26.5) - null means distance-based delivery pricing is
    // unavailable, whether because the address hasn't geocoded yet or address itself is blank.
    private Double latitude;
    private Double longitude;
    private BigDecimal deliveryBaseFee;
    private BigDecimal deliveryFeePerKm;
    private BigDecimal maxDeliveryDistanceKm;
    private String logo;
    private Integer tableCount;
    private Boolean active;
    private LocalDate paymentDueDate;
    private Boolean autoPrintKitchenTickets;
    private Integer kitchenWarningThresholdMinutes;
    private Integer kitchenCriticalThresholdMinutes;
    private Integer tableForgottenWarningThresholdMinutes;
    private Integer tableForgottenCriticalThresholdMinutes;
    private Boolean serviceChargeEnabled;
    private BigDecimal serviceChargePercentage;
    private Integer reservationBlockBeforeMinutes;
    private Integer reservationBlockAfterMinutes;
}
