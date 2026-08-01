ALTER TABLE products ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'SIMPLE';
ALTER TABLE products ADD COLUMN discount_percentage NUMERIC(5, 2);

CREATE TABLE IF NOT EXISTS combo_items (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    combo_product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    component_product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_combo_items_not_self CHECK (combo_product_id <> component_product_id)
);
CREATE INDEX IF NOT EXISTS idx_combo_items_restaurant_id ON combo_items(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_combo_items_combo_product_id ON combo_items(combo_product_id);
CREATE INDEX IF NOT EXISTS idx_combo_items_component_product_id ON combo_items(component_product_id);

CREATE TABLE IF NOT EXISTS combo_slots (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    combo_product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_combo_slots_restaurant_id ON combo_slots(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_combo_slots_combo_product_id ON combo_slots(combo_product_id);

CREATE TABLE IF NOT EXISTS combo_slot_options (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    slot_id UUID NOT NULL REFERENCES combo_slots(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_combo_slot_options_restaurant_id ON combo_slot_options(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_combo_slot_options_slot_id ON combo_slot_options(slot_id);
CREATE INDEX IF NOT EXISTS idx_combo_slot_options_product_id ON combo_slot_options(product_id);

ALTER TABLE order_items ADD COLUMN parent_order_item_id UUID REFERENCES order_items(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_order_items_parent_order_item_id ON order_items(parent_order_item_id);
