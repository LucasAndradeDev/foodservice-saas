CREATE TABLE IF NOT EXISTS warehouse_integrations (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL UNIQUE REFERENCES restaurants(id) ON DELETE CASCADE,
    api_key_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Brand new table, so RLS goes in the same migration that creates it (unlike V56, which had
-- to patch a gap in an already-applied one) - same policy shape as every other direct
-- restaurant_id table since V50 (see docs/RLS_DESIGN.md). app_runtime's grant on this table
-- comes from V46's ALTER DEFAULT PRIVILEGES, not an explicit GRANT here.
ALTER TABLE warehouse_integrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE warehouse_integrations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON warehouse_integrations
    USING (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
