-- Applies the same fix V65 gave card_charge_by_external_reference to its Pix equivalent (finding
-- #12, 2026-09-07 review): pix_charge_by_external_id (V58) has no lock, so two truly concurrent
-- Woovi webhook deliveries for the same charge (a real retry scenario the provider itself
-- documents) can both observe status == PENDING before either commits in
-- PixChargeService#handleWebhook. TabService#registerPayments's own lock on the Tab still stops
-- the double-credit, but the loser's write then throws an unhandled IllegalArgumentException,
-- surfacing as an unnecessary 500 to Woovi (which reasonably reads that as "retry me again").
-- FOR UPDATE serializes the two: the second delivery now blocks until the first's transaction
-- commits, then re-reads the already-PAID status and no-ops instead of erroring.
CREATE OR REPLACE FUNCTION pix_charge_by_external_id(p_external_id text)
RETURNS SETOF pix_charges
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM pix_charges WHERE external_charge_id = p_external_id FOR UPDATE;
$$;
