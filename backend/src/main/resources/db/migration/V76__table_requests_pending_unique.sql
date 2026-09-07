-- Closes a real (if low-impact) race in PublicTableRequestService#createRequest (finding #13,
-- 2026-09-07 review): the "is there already a pending request?" read and the insert aren't
-- atomic, so a double-click/retry on "chamar garçom" or "pedir a conta" could create two pending
-- rows for the same table+type. A partial unique index makes the database itself the source of
-- truth - the loser's INSERT now fails with a constraint violation instead of silently succeeding,
-- and the service catches that to return the winner's row (idempotent from the caller's view).
CREATE UNIQUE INDEX IF NOT EXISTS idx_table_requests_pending_unique
    ON table_requests (table_id, type)
    WHERE acknowledged_at IS NULL;
