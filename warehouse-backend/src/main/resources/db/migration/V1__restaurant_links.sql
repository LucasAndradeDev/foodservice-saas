CREATE TABLE IF NOT EXISTS restaurant_links (
    id UUID PRIMARY KEY,
    mora_restaurant_id UUID NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    -- Raw integration API key handed by Morá on each SSO handoff, sent back as a header on
    -- every GET /internal/warehouse/sales call. Morá is the one that stores a hash (it's the
    -- verifier); this side needs the plaintext value to send, not a hash of it.
    api_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_restaurant_links_mora_restaurant_id ON restaurant_links(mora_restaurant_id);
