package com.example.restaurant_saas.domain.entity;

import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.domain.enums.TabStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
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

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "paid_amount", precision = 10, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "receipt_printed_at")
    private OffsetDateTime receiptPrintedAt;

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
}
