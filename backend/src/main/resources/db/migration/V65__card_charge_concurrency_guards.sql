-- Closes a race between the async Mercado Pago webhook (CardChargeService#handleWebhook) and the
-- browser-triggered verify endpoint (CardChargeService#verifyPendingChargeByExternalReference,
-- see docs/CARD_PAYMENT.md) added alongside it: both read a CardCharge by external_reference,
-- check status == PENDING, make an outbound network call to Mercado Pago, and only then flip the
-- status - with no lock held across that window. Two concurrent callers can both observe PENDING
-- before either commits. A full-tab single charge is protected from double-crediting by
-- TabService#registerPayments's own pessimistic lock on the Tab row, but a split-bill charge
-- (CardChargeService#createCharge already supports a partial requestedAmount) can have enough
-- remaining balance headroom from an as-yet-uncharged sibling portion for both racing calls to
-- also pass that check, each inserting its own Payment row against the same real charge.

-- FOR UPDATE added to the existing lookup: both callers immediately act on the result inside the
-- same transaction, so locking the row here serializes the two paths before either can observe a
-- stale PENDING status - the second caller now blocks until the first's transaction commits (or
-- rolls back) and re-reads a settled PAID/DECLINED status instead of a stale PENDING one.
CREATE OR REPLACE FUNCTION card_charge_by_external_reference(p_external_reference text)
RETURNS SETOF card_charges
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM card_charges WHERE external_reference = p_external_reference FOR UPDATE;
$$;

-- Belt-and-suspenders independent of application-level locking: even if two transactions somehow
-- both got past the row lock above (e.g. a future caller that doesn't route through this
-- function), a second Payment row against the same already-linked CardCharge fails atomically at
-- the database level instead of silently duplicating a credit. Postgres allows any number of NULLs
-- under a plain UNIQUE constraint, so manual/Pix payments (card_charge_id always NULL) are
-- untouched.
ALTER TABLE payments ADD CONSTRAINT uq_payments_card_charge_id UNIQUE (card_charge_id);
