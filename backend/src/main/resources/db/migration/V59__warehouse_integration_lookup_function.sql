-- Narrow, deliberate RLS bypass for the one query that genuinely can't know the restaurant up
-- front: Armazém Morá's background sync job calls GET /api/v1/internal/warehouse/sales with only
-- its long-lived integration API key (hashed - see warehouse_integrations.api_key_hash, V57), no
-- JWT, no tenant. Same pattern as pix_charge_by_external_id (V58) and user_by_email (V52):
-- SECURITY DEFINER runs as the table-owning role, so it sees all rows regardless of app.tenant_id,
-- but only for this exact lookup, not a general grant on warehouse_integrations.
CREATE FUNCTION warehouse_integration_by_api_key_hash(p_api_key_hash text)
RETURNS SETOF warehouse_integrations
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM warehouse_integrations WHERE api_key_hash = p_api_key_hash;
$$;

REVOKE ALL ON FUNCTION warehouse_integration_by_api_key_hash(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION warehouse_integration_by_api_key_hash(text) TO app_runtime;
