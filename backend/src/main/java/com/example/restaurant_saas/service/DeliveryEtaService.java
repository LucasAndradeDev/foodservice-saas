package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.DeliveryStatus;
import com.example.restaurant_saas.repository.DeliveryDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Split out of {@link DeliveryService} on purpose - {@link #refreshEtaIfStale} needs its own
 * {@code REQUIRES_NEW} transaction (see its own javadoc), and Spring's transaction proxy only
 * ever intercepts a call that crosses a bean boundary. A {@code private} same-class helper
 * calling this via a bare {@code this.refreshEtaIfStale(...)} - the very first version of this,
 * caught in a self-review 2026-09-07 before ever reaching production - would silently skip the
 * proxy entirely: the "new" transaction never opens, the write happens inside whatever
 * transaction the caller (getByAccessToken's read-only one) already had open, and Hibernate's
 * MANUAL flush mode for read-only transactions means the eta_minutes/eta_updated_at write is
 * built in memory but never actually reaches the database - eta_updated_at reads back null on
 * every subsequent poll, so the throttle in {@link #refreshEtaIfStale} never engages and a live
 * routing-provider call fires on nearly every single 4-second customer poll instead of at most
 * once a minute. Being its own {@code @Service} is what makes {@link DeliveryService}'s call into
 * this class a real, proxied, cross-bean call.
 */
@Service
@RequiredArgsConstructor
public class DeliveryEtaService {

    // Mirrors DeliveryService.LOCATION_STALE_AFTER_MINUTES (same concept - a courier's last
    // reported position is trusted before treating them as gone dark) - kept as its own constant
    // here rather than shared, since the two classes exist for an unrelated reason (see class
    // javadoc) and a shared constant would suggest a coupling that isn't really there.
    private static final long LOCATION_STALE_AFTER_MINUTES = 5;

    // How often this actually calls a routing provider, independent of how often the customer's
    // tracking page polls (4s) - keeps a busy restaurant's several simultaneous deliveries from
    // burning through ORS's daily free quota just from customers leaving the tracking page open.
    // A minute-old ETA is still a perfectly reasonable "chegada estimada".
    private static final long ETA_REFRESH_INTERVAL_SECONDS = 60;

    private final DeliveryDetailsRepository deliveryDetailsRepository;
    private final RouteDistanceService routeDistanceService;
    private final TenantActivator tenantActivator;

    /**
     * Recomputes the live ETA from the assigned courier's current position, at most once every
     * {@value #ETA_REFRESH_INTERVAL_SECONDS} seconds, and returns the freshly computed minutes
     * when it did (empty otherwise - either nothing needed recomputing, or the routing provider
     * chain came up empty). A no-op whenever there's nothing useful to compute from yet: not
     * OUT_FOR_DELIVERY, the customer's address was never geocoded (DeliveryZone-priced order), or
     * the courier's last reported position has already gone stale - same conditions
     * {@link DeliveryService#toResponse} already gates showing the courier's position on.
     * REQUIRES_NEW so this is safe to call from inside getByAccessToken's read-only transaction
     * (that method's own REQUIRES_NEW sibling, {@link CardChargeService#verifyPendingChargeByExternalReference},
     * documents why this pattern is needed) - and reactivates the tenant context itself, since
     * REQUIRES_NEW can land on a different pooled connection than the caller's already-activated
     * one. Never throws - callers wrap this in their own best-effort try/catch (see
     * DeliveryService#refreshEtaBestEffort), same pattern as verifyPendingCardCharge - a
     * routing-provider hiccup must never break the customer's tracking page read.
     *
     * <p>Returning the value (rather than having the caller re-fetch the entity) is deliberate:
     * the caller's own persistence context already has this row's entity loaded from earlier in
     * the same transaction, and JPA's {@code find()}/{@code findById()} resolves against that
     * first-level cache by id before ever hitting the database - so a second {@code findById}
     * call there would silently hand back the same pre-update in-memory object instead of this
     * method's committed write, showing null/stale on exactly the poll that just computed it
     * (self-correcting on the next poll, 4s later, once the cache entry itself has since been
     * reloaded some other way - subtle enough to slip past casual testing).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Integer> refreshEtaIfStale(UUID restaurantId, UUID deliveryDetailsId) {
        tenantActivator.activate(restaurantId);
        try {
            DeliveryDetails d = deliveryDetailsRepository.findById(deliveryDetailsId).orElse(null);
            if (d == null || d.getStatus() != DeliveryStatus.OUT_FOR_DELIVERY
                    || d.getCustomerLatitude() == null || d.getCustomerLongitude() == null) {
                return Optional.empty();
            }
            if (d.getEtaUpdatedAt() != null
                    && d.getEtaUpdatedAt().isAfter(OffsetDateTime.now().minusSeconds(ETA_REFRESH_INTERVAL_SECONDS))) {
                return Optional.empty();
            }
            User courier = d.getCourier();
            if (courier == null || courier.getLocationUpdatedAt() == null
                    || courier.getLocationUpdatedAt().isBefore(OffsetDateTime.now().minusMinutes(LOCATION_STALE_AFTER_MINUTES))) {
                return Optional.empty();
            }

            return routeDistanceService.route(courier.getLatitude(), courier.getLongitude(), d.getCustomerLatitude(), d.getCustomerLongitude())
                    .map(route -> {
                        int etaMinutes = (int) Math.ceil(route.durationMinutes());
                        d.setEtaMinutes(etaMinutes);
                        d.setEtaUpdatedAt(OffsetDateTime.now());
                        deliveryDetailsRepository.save(d);
                        return etaMinutes;
                    });
        } finally {
            tenantActivator.deactivate();
        }
    }
}
