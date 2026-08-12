-- Pix payment gateway integration (Woovi), see docs/PIX_PAYMENT.md.
-- Two new tables, same RLS shape as every direct restaurant_id table since V50 (RLS_DESIGN.md).
-- app_runtime's grant on both comes from V46's ALTER DEFAULT PRIVILEGES, not an explicit GRANT here.

-- One row per restaurant: holds the Woovi AppID needed to call their API on this restaurant's
-- behalf. api_key_encrypted is reversible (AES/GCM via CredentialEncryptionService), unlike
-- warehouse_integrations.api_key_hash (V57) - that one only ever needs to be *compared*, this one
-- needs to be *sent back* to Woovi on every charge, so a one-way hash would be useless here.
CREATE TABLE pix_integrations (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL UNIQUE REFERENCES restaurants(id) ON DELETE CASCADE,
    api_key_encrypted TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE pix_integrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE pix_integrations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pix_integrations
    USING (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- One row per Pix charge created for a tab. restaurant_id is stored directly (not just reachable
-- via tab_id) so the RLS filter here works the same simple way as every other direct table.
CREATE TABLE pix_charges (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    tab_id UUID NOT NULL REFERENCES tabs(id) ON DELETE CASCADE,
    external_charge_id VARCHAR(255) NOT NULL UNIQUE,
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE pix_charges ENABLE ROW LEVEL SECURITY;
ALTER TABLE pix_charges FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pix_charges
    USING (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Narrow, deliberate RLS bypass for the one query that genuinely can't know the restaurant up
-- front: the Woovi webhook arrives with only the external_charge_id (Woovi's correlationID), no
-- JWT, no tenant. Same pattern as reservation_by_access_token (V49) - SECURITY DEFINER runs as
-- the table-owning role, so it sees all rows regardless of app.tenant_id, but only for this exact
-- lookup, not a general grant on pix_charges.
CREATE FUNCTION pix_charge_by_external_id(p_external_id text)
RETURNS SETOF pix_charges
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM pix_charges WHERE external_charge_id = p_external_id;
$$;

REVOKE ALL ON FUNCTION pix_charge_by_external_id(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION pix_charge_by_external_id(text) TO app_runtime;
