package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.TableRequest;
import com.example.restaurant_saas.repository.TableRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out of {@link PublicTableRequestService} on purpose - same reason {@link
 * DeliveryEtaService} is its own class (see its javadoc): {@link #insert} needs its own {@code
 * REQUIRES_NEW} transaction so a unique-constraint violation there rolls back only this insert
 * attempt, on its own pooled connection, instead of aborting the caller's whole transaction at the
 * Postgres level. A same-class private method calling this via a bare {@code this.insert(...)}
 * would skip Spring's proxy entirely and it would just run inside the caller's already-open
 * transaction, defeating the point - confirmed live under real concurrency: the first version of
 * this fix (a plain try/catch around {@code saveAndFlush} inside {@link
 * PublicTableRequestService#createRequest}) turned every losing request into a raw 500 instead of
 * the intended graceful fallback, because Postgres had already aborted the whole transaction by
 * the time the catch block tried its own fallback read on that same connection.
 */
@Service
@RequiredArgsConstructor
public class TableRequestInsertService {

    private final TableRequestRepository tableRequestRepository;
    private final TenantActivator tenantActivator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TableRequest insert(TableRequest request) {
        // Belt and suspenders: TenantAwareDataSource already stamps every connection it hands out
        // (including this REQUIRES_NEW transaction's own fresh one) from the current thread's
        // TenantContext, which the caller already set before calling this - but activating here
        // too costs nothing and matches DeliveryEtaService's own precedent for a REQUIRES_NEW
        // method that can't assume how it might be reached in the future.
        // request.getRestaurant() is the same already-loaded Restaurant instance the caller
        // passed in (not a lazy proxy needing another query) - reading its id is free.
        tenantActivator.activate(request.getRestaurant().getId());
        try {
            return tableRequestRepository.saveAndFlush(request);
        } finally {
            tenantActivator.deactivate();
        }
    }
}
