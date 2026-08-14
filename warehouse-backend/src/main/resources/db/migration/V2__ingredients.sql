CREATE TABLE IF NOT EXISTS ingredients (
    id UUID PRIMARY KEY,
    restaurant_link_id UUID NOT NULL REFERENCES restaurant_links(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    current_quantity NUMERIC(12,3) NOT NULL DEFAULT 0,
    low_stock_threshold NUMERIC(12,3),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ingredients_restaurant_link_id ON ingredients(restaurant_link_id);
