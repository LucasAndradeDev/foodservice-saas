package com.example.restaurant_saas.domain.entity;

import com.example.restaurant_saas.domain.enums.CardChargeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "card_charges")
@Filter(name = "tenantFilter", condition = "restaurant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tab_id", nullable = false)
    private Tab tab;

    // Our own correlation id, sent to Mercado Pago as external_reference on the preference - the
    // field the webhook's follow-up GET /v1/payments/{id} response echoes back (see
    // card_charge_by_external_reference, V61). Never trust the webhook body's own content for
    // this - it doesn't carry external_reference at all, by design on Mercado Pago's side.
    @Column(name = "external_reference", nullable = false, unique = true)
    private String externalReference;

    // Mercado Pago's own payment id - only known once the webhook's authoritative fetch succeeds.
    // Distinct from externalReference: this is the id a refund is issued against, not the
    // preference id returned at creation time.
    @Column(name = "mp_payment_id")
    private String mpPaymentId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardChargeStatus status;

    // Raw Mercado Pago status_detail code, kept for audit even though the user-facing message
    // (CardDeclineMessages) is derived from it, not stored verbatim.
    @Column(name = "status_detail")
    private String statusDetail;

    @Column(name = "init_point_url")
    private String initPointUrl;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @Column(name = "refund_id")
    private String refundId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
