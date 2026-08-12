package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.PixCharge;
import com.example.restaurant_saas.domain.enums.PixChargeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
