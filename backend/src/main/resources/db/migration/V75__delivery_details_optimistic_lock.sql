-- Optimistic lock for delivery_details (finding #9, 2026-09-07 security review): staff actions
-- (assign/reassign courier, status changes) and the throttled ETA background refresh
-- (DeliveryEtaService, its own REQUIRES_NEW transaction) can write concurrently with no lock
-- today, so the loser's write silently vanishes (last-write-wins). Default 0 backfills existing
-- rows so the column is usable immediately.
ALTER TABLE delivery_details
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
