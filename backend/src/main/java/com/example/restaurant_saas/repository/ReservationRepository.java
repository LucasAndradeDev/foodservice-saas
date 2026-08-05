package com.example.restaurant_saas.repository;

import com.example.restaurant_saas.domain.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Optional<Reservation> findByAccessToken(String accessToken);

    List<Reservation> findByRestaurantIdAndReservationTimeBetweenOrderByReservationTimeAsc(
            UUID restaurantId, OffsetDateTime start, OffsetDateTime end);

    // Used both for the live "can this table be opened right now" check in TabService and for the
    // re-confirmation (under lock) of a specific candidate table set while creating a new reservation.
    // The caller computes windowStart/windowEnd differently depending on which of those two questions
    // it's asking — see ReservationService for the exact derivation of each.
    @Query("SELECT COUNT(r) > 0 FROM Reservation r JOIN r.tables t " +
            "WHERE t.id IN :tableIds AND r.status = 'SCHEDULED' " +
            "AND r.reservationTime BETWEEN :windowStart AND :windowEnd")
    boolean existsBlockingReservation(
            @Param("tableIds") List<UUID> tableIds,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("windowEnd") OffsetDateTime windowEnd);

    // All table ids blocked anywhere in the restaurant within the window, used to filter the candidate
    // pool when auto-assigning a table for a new reservation.
    @Query("SELECT DISTINCT t.id FROM Reservation r JOIN r.tables t " +
            "WHERE r.restaurant.id = :restaurantId AND r.status = 'SCHEDULED' " +
            "AND r.reservationTime BETWEEN :windowStart AND :windowEnd")
    List<UUID> findBlockedTableIds(
            @Param("restaurantId") UUID restaurantId,
            @Param("windowStart") OffsetDateTime windowStart,
            @Param("windowEnd") OffsetDateTime windowEnd);

    // Cosmetic only (see ReservationService): flips stale SCHEDULED reservations to NO_SHOW so the
    // staff list stops showing them as upcoming. The live blocking check above never relies on this —
    // it already self-expires once "now" falls outside a reservation's window, status update or not.
    @Modifying
    @Query("UPDATE Reservation r SET r.status = 'NO_SHOW' WHERE r.restaurant.id = :restaurantId " +
            "AND r.status = 'SCHEDULED' AND r.reservationTime < :cutoff")
    int expireNoShows(@Param("restaurantId") UUID restaurantId, @Param("cutoff") OffsetDateTime cutoff);
}
