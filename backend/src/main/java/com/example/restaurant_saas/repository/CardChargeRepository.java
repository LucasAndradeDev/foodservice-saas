package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.CardCharge;
import com.example.restaurant_saas.domain.enums.CardChargeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardChargeRepository extends JpaRepository<CardCharge, UUID> {

    // card_charge_by_external_reference (V61) is the only way to find this row before the tenant
    // is known - the Mercado Pago webhook body carries no external_reference at all, only a
    // payment id; this lookup runs after the authoritative GET /v1/payments/{id} fetch, using the
    // external_reference that response returns. Same pattern as
    // PixChargeRepository#findByExternalChargeIdBypassingRls.
    // Locks the row (FOR UPDATE, added in V65) - both callers (CardChargeService#handleWebhook and
    // #verifyPendingChargeByExternalReference) check status == PENDING and then make an outbound
    // call to Mercado Pago before acting, so without a lock two concurrent callers (the async
    // webhook racing the browser-triggered verify call, or two verify calls) could both observe a
    // stale PENDING status and each register their own Payment for the same real charge - a real
    // risk specifically on a split-bill tab, where a still-uncommitted sibling portion provides the
    // remaining-balance headroom for both to pass TabService#registerPayments's own check too. The
    // lock serializes them: the second caller blocks until the first's transaction commits, then
    // re-reads the now-settled PAID/DECLINED status instead of duplicating the credit.
    @Query(value = "SELECT * FROM card_charge_by_external_reference(:externalReference)", nativeQuery = true)
    Optional<CardCharge> findByExternalReferenceBypassingRls(@Param("externalReference") String externalReference);

    // A tab can accumulate more than one PENDING row if staff regenerates the checkout link (each
    // attempt gets its own external_reference) - cancelling needs to catch all of them, not just
    // the latest, so a stale one's late webhook can't slip past the CANCELLED check later.
    List<CardCharge> findByTab_IdAndStatus(UUID tabId, CardChargeStatus status);

    // How much is currently "spoken for" by charges in a given status on this tab - used both to
    // stop a new charge from overcommitting past what's actually still owed (PENDING) and,
    // elsewhere, to list what's outstanding.
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CardCharge c WHERE c.tab.id = :tabId AND c.status = :status")
    BigDecimal sumAmountByTabIdAndStatus(@Param("tabId") UUID tabId, @Param("status") CardChargeStatus status);

    // PENDING+PAID (not CANCELLED/DECLINED/EXPIRED, those are dead) - lets the Checkout poll every
    // outstanding card charge at once and tell "just paid" (status flips to PAID) apart from
    // "declined/cancelled/expired elsewhere" (the row drops out of this list entirely).
    List<CardCharge> findByTab_IdAndRestaurantIdAndStatusIn(UUID tabId, UUID restaurantId, List<CardChargeStatus> statuses);

    // Explicit restaurantId filter in the query itself (not just relying on the Hibernate tenant
    // filter) - same defense-in-depth as PaymentRepository#findByIdAndTabIdAndRestaurantId, since
    // this backs a cancel-a-specific-charge endpoint a client supplies the id for.
    Optional<CardCharge> findByIdAndTab_IdAndRestaurantId(UUID id, UUID tabId, UUID restaurantId);
}
