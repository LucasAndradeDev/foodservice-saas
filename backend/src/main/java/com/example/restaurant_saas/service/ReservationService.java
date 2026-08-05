package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Reservation;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.enums.ReservationStatus;
import com.example.restaurant_saas.domain.enums.TableStatus;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.response.ReservationResponse;
import com.example.restaurant_saas.dto.response.ReservationTableSummary;
import com.example.restaurant_saas.dto.response.TabResponse;
import com.example.restaurant_saas.repository.ReservationRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import com.example.restaurant_saas.repository.TabRepository;
import com.example.restaurant_saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RestaurantTableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final TabRepository tabRepository;
    private final TabService tabService;

    @Transactional
    public ReservationResponse createReservation(
            UUID restaurantId,
            String customerName,
            String customerPhone,
            String note,
            Integer partySize,
            OffsetDateTime reservationTime,
            UUID createdByUserId,
            List<UUID> explicitTableIds
    ) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found."));

        Window conflictWindow = conflictWindow(restaurant, reservationTime);

        List<UUID> chosenTableIds = (explicitTableIds != null && !explicitTableIds.isEmpty())
                ? validateExplicitTables(restaurantId, explicitTableIds, partySize, conflictWindow)
                : autoAssignTables(restaurantId, partySize, conflictWindow).stream().map(RestaurantTable::getId).toList();

        // Lock the chosen tables in ascending id order (never the request's own order) so two
        // concurrent reservations competing for an overlapping set of tables always acquire their
        // locks in the same sequence — the standard way to avoid a deadlock between the two
        // transactions. Re-running the conflict check once locked closes the race where another
        // reservation for the same table/time slipped in between the check above and this lock.
        List<UUID> sortedIds = chosenTableIds.stream().sorted().toList();
        List<RestaurantTable> lockedTables = new ArrayList<>();
        for (UUID id : sortedIds) {
            lockedTables.add(tableRepository.findByIdAndRestaurantIdForUpdate(id, restaurantId)
                    .orElseThrow(() -> new IllegalArgumentException("Table not found.")));
        }
        if (reservationRepository.existsBlockingReservation(sortedIds, conflictWindow.start(), conflictWindow.end())) {
            throw new IllegalStateException("No table available for the requested party size and time.");
        }

        Reservation reservation = Reservation.builder()
                .restaurant(restaurant)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .note(note)
                .partySize(partySize)
                .reservationTime(reservationTime)
                .status(ReservationStatus.SCHEDULED)
                .accessToken(UUID.randomUUID().toString())
                .createdBy(createdByUserId != null ? userRepository.getReferenceById(createdByUserId) : null)
                .tables(lockedTables)
                .build();

        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public List<ReservationResponse> listReservations(UUID restaurantId, LocalDate date) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found."));

        // Cosmetic sweep only: flips stale SCHEDULED reservations to NO_SHOW so the list stops showing
        // them as upcoming. The live blocking check in TabService never depends on this running.
        reservationRepository.expireNoShows(restaurantId, OffsetDateTime.now().minusMinutes(restaurant.getReservationBlockAfterMinutes()));

        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime start = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        return reservationRepository.findByRestaurantIdAndReservationTimeBetweenOrderByReservationTimeAsc(restaurantId, start, end)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReservationResponse checkIn(UUID restaurantId, UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdAndRestaurantId(reservationId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.SCHEDULED && reservation.getStatus() != ReservationStatus.NO_SHOW) {
            throw new IllegalStateException("Reservation cannot be checked in from its current status.");
        }

        // Flip to SEATED before opening the tab: TabService.openTab rejects opening any table that has
        // an active SCHEDULED reservation blocking it right now -- including this very reservation.
        // Moving it out of SCHEDULED first removes it from that check without TabService needing a
        // special "ignore this reservation" parameter, so the blocking rule stays in one place with no
        // exceptions carved into it.
        reservation.setStatus(ReservationStatus.SEATED);
        reservationRepository.save(reservation);

        OpenTabRequest openTabRequest = new OpenTabRequest();
        openTabRequest.setTableIds(reservation.getTables().stream().map(RestaurantTable::getId).toList());
        TabResponse tabResponse = tabService.openTab(restaurantId, openTabRequest);

        reservation.setTab(tabRepository.getReferenceById(tabResponse.getId()));
        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationResponse cancelByStaff(UUID restaurantId, UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdAndRestaurantId(reservationId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        return cancel(reservation);
    }

    @Transactional
    public ReservationResponse cancelByToken(String token) {
        Reservation reservation = reservationRepository.findByAccessToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        return cancel(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getByToken(String token) {
        return toResponse(reservationRepository.findByAccessToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found.")));
    }

    private ReservationResponse cancel(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.SCHEDULED && reservation.getStatus() != ReservationStatus.NO_SHOW) {
            throw new IllegalStateException("Reservation cannot be cancelled from its current status.");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        return toResponse(reservationRepository.save(reservation));
    }

    private List<UUID> validateExplicitTables(UUID restaurantId, List<UUID> tableIds, int partySize, Window conflictWindow) {
        List<RestaurantTable> tables = tableRepository.findByIdInAndRestaurantId(tableIds, restaurantId);
        if (tables.size() != tableIds.size()) {
            throw new IllegalArgumentException("One or more tables were not found in this restaurant.");
        }
        if (tables.stream().anyMatch(t -> !Boolean.TRUE.equals(t.getActive()))) {
            throw new IllegalArgumentException("One or more tables are not active.");
        }
        int totalCapacity = tables.stream().mapToInt(RestaurantTable::getCapacity).sum();
        if (totalCapacity < partySize) {
            throw new IllegalArgumentException("Selected tables do not have enough capacity for this party size.");
        }
        List<UUID> ids = tables.stream().map(RestaurantTable::getId).toList();
        if (reservationRepository.existsBlockingReservation(ids, conflictWindow.start(), conflictWindow.end())) {
            throw new IllegalStateException("One or more selected tables are already reserved around this time.");
        }
        return ids;
    }

    // Picks the smallest single table that fits the party, or -- if none does -- the best pair of
    // tables (preferring the same dining area, then minimizing leftover seats). Never combines more
    // than two tables: larger groups can still be booked by staff picking tables manually.
    private List<RestaurantTable> autoAssignTables(UUID restaurantId, int partySize, Window conflictWindow) {
        List<UUID> blockedIds = reservationRepository.findBlockedTableIds(restaurantId, conflictWindow.start(), conflictWindow.end());
        List<RestaurantTable> candidates = tableRepository.findByRestaurantId(restaurantId).stream()
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .filter(t -> !blockedIds.contains(t.getId()))
                // A table with an open tab right now (walk-in, not from a reservation) is never a safe
                // auto-pick: there's no guarantee it frees up in time, and picking it anyway just moves
                // the failure to check-in time with a confusing error. Staff choosing tables explicitly
                // can still override this by picking the table directly.
                .filter(t -> t.getStatus() == TableStatus.FREE)
                .toList();

        RestaurantTable bestSingle = candidates.stream()
                .filter(t -> t.getCapacity() >= partySize)
                .min(Comparator.comparingInt(RestaurantTable::getCapacity))
                .orElse(null);
        if (bestSingle != null) {
            return List.of(bestSingle);
        }

        List<RestaurantTable> bestPair = null;
        int bestExcess = Integer.MAX_VALUE;
        boolean bestSameArea = false;
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                RestaurantTable a = candidates.get(i);
                RestaurantTable b = candidates.get(j);
                int total = a.getCapacity() + b.getCapacity();
                if (total < partySize) {
                    continue;
                }
                boolean sameArea = Objects.equals(areaId(a), areaId(b));
                int excess = total - partySize;
                boolean better = bestPair == null
                        || (sameArea && !bestSameArea)
                        || (sameArea == bestSameArea && excess < bestExcess);
                if (better) {
                    bestPair = List.of(a, b);
                    bestExcess = excess;
                    bestSameArea = sameArea;
                }
            }
        }
        if (bestPair == null) {
            throw new IllegalStateException("No table available for the requested party size and time.");
        }
        return bestPair;
    }

    private UUID areaId(RestaurantTable table) {
        return table.getArea() != null ? table.getArea().getId() : null;
    }

    // Two different questions share the same underlying query (see ReservationRepository):
    // - "is this table blocked right now" (TabService, computed there): now in [T-before, T+after]
    //   <=> T in [now-after, now+before].
    // - "would a new reservation at Tnew overlap an existing one at Texisting" (here): their windows
    //   [T-before, T+after] overlap iff Texisting is within (before+after) minutes of Tnew in either
    //   direction.
    private Window conflictWindow(Restaurant restaurant, OffsetDateTime candidateTime) {
        int span = restaurant.getReservationBlockBeforeMinutes() + restaurant.getReservationBlockAfterMinutes();
        return new Window(candidateTime.minusMinutes(span), candidateTime.plusMinutes(span));
    }

    private record Window(OffsetDateTime start, OffsetDateTime end) {
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .restaurantId(reservation.getRestaurant().getId())
                .customerName(reservation.getCustomerName())
                .customerPhone(reservation.getCustomerPhone())
                .note(reservation.getNote())
                .partySize(reservation.getPartySize())
                .reservationTime(reservation.getReservationTime())
                .status(reservation.getStatus())
                .accessToken(reservation.getAccessToken())
                .tabId(reservation.getTab() != null ? reservation.getTab().getId() : null)
                .tables(reservation.getTables().stream()
                        .map(t -> ReservationTableSummary.builder().id(t.getId()).number(t.getNumber()).build())
                        .toList())
                .createdAt(reservation.getCreatedAt())
                .build();
    }
}
