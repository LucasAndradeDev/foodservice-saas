package com.example.restaurant_saas.config;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * A per-thread STACK of tenant ids, not a single slot. {@code JwtAuthenticationFilter} pushes once
 * per authenticated request and pops (via {@link #clear()}, a hard reset of the whole stack -
 * the request's own safety net against thread-pool reuse) when the request ends; {@link
 * TenantActivator} pushes/pops around the narrower window where a bypassed-RLS lookup resolves a
 * tenant *inside* an already-open request (e.g. verifying a card charge while listing deliveries).
 * <p>
 * A single mutable slot would be wrong here: {@code TenantActivator}'s own nested
 * activate/deactivate calls can run *inside* an outer request that already has its own tenant set
 * (e.g. {@code DeliveryService#verifyPendingCardCharge}, called mid-way through the authenticated
 * {@code listOpenDeliveries}). A plain "clear" on the nested call's way out would wipe the outer
 * request's tenant instead of just its own - harmless today only because nothing after that point
 * happens to need a fresh connection checkout, but a real footgun for the next thing built on this
 * pattern. Popping back to whatever was on the stack before the nested push fixes that at the
 * source, with the outer request-level {@link #clear()} left as an unconditional reset so a
 * missed/unbalanced pop still can never survive into the next request that reuses this thread.
 */
public class TenantContext {

    private static final ThreadLocal<Deque<UUID>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void setCurrentTenant(UUID tenantId) {
        STACK.get().push(tenantId);
    }

    public static UUID getCurrentTenant() {
        return STACK.get().peek();
    }

    /** Pops back to whatever tenant (if any) was active before the matching {@link #setCurrentTenant}. */
    public static void popCurrentTenant() {
        Deque<UUID> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /** Hard reset - drops the whole stack, not just one frame. Request-level entrypoints only. */
    public static void clear() {
        STACK.remove();
    }
}
