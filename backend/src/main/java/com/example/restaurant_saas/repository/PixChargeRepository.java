package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.PixCharge;
import com.example.restaurant_saas.domain.enums.PixChargeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PixChargeRepository extends JpaRepository<PixCharge, UUID> {

    // pix_charge_by_external_id (V58) is the only way to find this row before the tenant is
    // known - the Woovi webhook arrives with no JWT, just the external_charge_id. Same pattern as
    // ReservationRepository#findByAccessTokenBypassingRls.
    @Query(value = "SELECT * FROM pix_charge_by_external_id(:externalChargeId)", nativeQuery = true)
    Optional<PixCharge> findByExternalChargeIdBypassingRls(@Param("externalChargeId") String externalChargeId);

    // A tab can accumulate more than one PENDING row if staff regenerates the QR code (each
    // attempt gets its own correlationID) - cancelling needs to catch all of them, not just the
    // latest, so a stale one's late webhook can't slip past the CANCELLED check later.
    List<PixCharge> findByTab_IdAndStatus(UUID tabId, PixChargeStatus status);

    // How much is currently "spoken for" by charges in a given status on this tab - used both to
    // stop a new charge from overcommitting past what's actually still owed (PENDING) and,
    // elsewhere, to list what's outstanding.
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM PixCharge c WHERE c.tab.id = :tabId AND c.status = :status")
    BigDecimal sumAmountByTabIdAndStatus(@Param("tabId") UUID tabId, @Param("status") PixChargeStatus status);

    // PENDING+PAID (not CANCELLED/EXPIRED, those are dead) - lets the Checkout poll every
    // outstanding split-payment QR code at once and tell "just paid" (status flips to PAID) apart
    // from "cancelled/expired elsewhere" (the row drops out of this list entirely).
    List<PixCharge> findByTab_IdAndRestaurantIdAndStatusIn(UUID tabId, UUID restaurantId, List<PixChargeStatus> statuses);

    // Explicit restaurantId filter in the query itself (not just relying on the Hibernate tenant
    // filter) - same defense-in-depth as PaymentRepository#findByIdAndTabIdAndRestaurantId, since
    // this backs a cancel-a-specific-charge endpoint a client supplies the id for.
    Optional<PixCharge> findByIdAndTab_IdAndRestaurantId(UUID id, UUID tabId, UUID restaurantId);
}
