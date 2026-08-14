CREATE TABLE IF NOT EXISTS suppliers (
    id UUID PRIMARY KEY,
    restaurant_link_id UUID NOT NULL REFERENCES restaurant_links(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    contact VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_suppliers_restaurant_link_id ON suppliers(restaurant_link_id);

CREATE TABLE IF NOT EXISTS purchases (
    id UUID PRIMARY KEY,
    restaurant_link_id UUID NOT NULL REFERENCES restaurant_links(id) ON DELETE CASCADE,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    purchased_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_purchases_restaurant_link_id ON purchases(restaurant_link_id);
CREATE INDEX IF NOT EXISTS idx_purchases_supplier_id ON purchases(supplier_id);

CREATE TABLE IF NOT EXISTS purchase_items (
    id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id),
    quantity NUMERIC(12,3) NOT NULL,
    unit_cost NUMERIC(12,2)
);

CREATE INDEX IF NOT EXISTS idx_purchase_items_purchase_id ON purchase_items(purchase_id);
