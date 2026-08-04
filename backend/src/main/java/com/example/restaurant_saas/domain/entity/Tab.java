package com.example.restaurant_saas.domain.entity;

import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.TabStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tabs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tab {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TabStatus status;

    @Column(name = "opened_at")
    private OffsetDateTime openedAt;

    @Column(name = "last_order_at")
    private OffsetDateTime lastOrderAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "bill_total", precision = 10, scale = 2)
    private BigDecimal billTotal;

    @Column(name = "receipt_printed_at")
    private OffsetDateTime receiptPrintedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "discount_reason", length = 255)
    private String discountReason;

    @Column(name = "discount_applied_by", length = 255)
    private String discountAppliedBy;

    @Column(name = "discount_applied_at")
    private OffsetDateTime discountAppliedAt;

    @Column(name = "service_charge_percentage", precision = 5, scale = 2)
    private BigDecimal serviceChargePercentage;

    @Column(name = "service_charge_amount", precision = 10, scale = 2)
    private BigDecimal serviceChargeAmount;

    @ManyToMany
    @JoinTable(
            name = "tab_tables",
            joinColumns = @JoinColumn(name = "tab_id"),
            inverseJoinColumns = @JoinColumn(name = "table_id")
    )
    @Builder.Default
    private List<RestaurantTable> tables = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public BigDecimal getDiscountAmount(BigDecimal itemsTotal) {
        if (discountType == null || discountValue == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = discountType == DiscountType.PERCENTAGE
                ? itemsTotal.multiply(discountValue).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : discountValue;
        return amount.min(itemsTotal);
    }
}
