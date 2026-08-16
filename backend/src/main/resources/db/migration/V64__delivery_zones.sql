-- Delivery feature (docs/DELIVERY.md), task 26.1: neighborhood -> fixed fee list, no geocoding in
-- v1. One row per (restaurant, neighborhood); matched case-insensitively against the customer's
-- typed neighborhood at quote/checkout time (task 26.3/26.4) - no CEP-range parsing in v1, same
-- "simplest thing that solves the real use case" spirit as the rest of the project.
CREATE TABLE delivery_zones (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    neighborhood VARCHAR(100) NOT NULL,
    fee NUMERIC(10, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE delivery_zones ENABLE ROW LEVEL SECURITY;
ALTER TABLE delivery_zones FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON delivery_zones
    USING (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
