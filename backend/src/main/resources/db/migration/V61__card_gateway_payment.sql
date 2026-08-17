-- Card payment gateway integration (Mercado Pago Checkout Pro), see docs/CARD_PAYMENT.md.
-- Two new tables, same RLS shape as pix_integrations/pix_charges (V58). app_runtime's grant on
-- both comes from V46's ALTER DEFAULT PRIVILEGES, not an explicit GRANT here.

-- One row per restaurant: holds the Mercado Pago Access Token needed to call their API on this
-- restaurant's behalf, plus the webhook signing secret (per-application, distinct from the
-- access token, confirmed against developers.mercadopago.com during design - unlike Woovi, whose
-- webhook key is a single fixed public key shared by every account). Both encrypted the same
-- reversible way (AES/GCM via CredentialEncryptionService) as pix_integrations.api_key_encrypted -
-- the access token must be sent back to Mercado Pago on every call, and the webhook secret must be
-- used to compute an HMAC, so neither can be a one-way hash. webhook_secret_encrypted is nullable
-- at the DB level (a restaurant could in principle save an access token before generating a
-- webhook secret in Mercado Pago's panel), but the service layer only reports "configured" once
-- both are present.
CREATE TABLE card_integrations (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL UNIQUE REFERENCES restaurants(id) ON DELETE CASCADE,
    access_token_encrypted TEXT NOT NULL,
    webhook_secret_encrypted TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE card_integrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE card_integrations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON card_integrations
    USING (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- One row per card charge (a Mercado Pago Checkout Pro "preference") created for a tab.
-- restaurant_id is stored directly (not just reachable via tab_id) so the RLS filter here works
-- the same simple way as every other direct table.
CREATE TABLE card_charges (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    tab_id UUID NOT NULL REFERENCES tabs(id) ON DELETE CASCADE,
    -- Our own UUID, sent to Mercado Pago as external_reference on the preference - the field the
    -- webhook's follow-up GET /v1/payments/{id} response echoes back, letting the confirmed
    -- payment find its way back to this row (see card_charge_by_external_reference below).
    external_reference VARCHAR(255) NOT NULL UNIQUE,
    -- Mercado Pago's own numeric payment id - only known once the webhook's GET /v1/payments/{id}
    -- fetch succeeds. Distinct from external_reference: this is the id a refund is issued against
    -- (POST /v1/payments/{id}/refunds), not the preference id Mercado Pago hands back at creation.
    mp_payment_id VARCHAR(255),
    amount NUMERIC(10, 2) NOT NULL,
    -- One more state than pix_charges.status: DECLINED. A card payment can genuinely fail
    -- synchronously (Pix charges only ever expire or get cancelled, never "decline").
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- Raw Mercado Pago status_detail code (e.g. cc_rejected_insufficient_amount), kept for
    -- support/audit even though the user-facing message is derived from it, not stored verbatim.
    status_detail VARCHAR(100),
    init_point_url TEXT,
    refunded_at TIMESTAMP WITH TIME ZONE,
    refund_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE card_charges ENABLE ROW LEVEL SECURITY;
ALTER TABLE card_charges FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON card_charges
    USING (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Narrow, deliberate RLS bypass for the one query that genuinely can't know the restaurant up
-- front from the webhook body alone (Mercado Pago's notification payload is minimal - just a
-- payment id, no external_reference; the restaurant is instead resolved from a {restaurantId}
-- path segment on the webhook URL itself, but this lookup by external_reference is still needed
-- once the authoritative GET /v1/payments/{id} response is in hand). Same pattern as
-- pix_charge_by_external_id (V58) / reservation_by_access_token (V49): SECURITY DEFINER runs as
-- the table-owning role, so it sees all rows regardless of app.tenant_id, but only for this exact
-- lookup, not a general grant on card_charges.
CREATE FUNCTION card_charge_by_external_reference(p_external_reference text)
RETURNS SETOF card_charges
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM card_charges WHERE external_reference = p_external_reference;
$$;

REVOKE ALL ON FUNCTION card_charge_by_external_reference(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION card_charge_by_external_reference(text) TO app_runtime;
