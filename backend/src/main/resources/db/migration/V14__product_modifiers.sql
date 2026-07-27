CREATE TABLE IF NOT EXISTS product_modifier_groups (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    selection_type VARCHAR(20) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_product_modifier_groups_restaurant_id ON product_modifier_groups(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_product_modifier_groups_product_id ON product_modifier_groups(product_id);

CREATE TABLE IF NOT EXISTS product_modifier_options (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES product_modifier_groups(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    price_delta NUMERIC(10, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_product_modifier_options_restaurant_id ON product_modifier_options(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_product_modifier_options_group_id ON product_modifier_options(group_id);

CREATE TABLE IF NOT EXISTS order_item_modifiers (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    order_item_id UUID NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    group_name VARCHAR(100) NOT NULL,
    option_name VARCHAR(100) NOT NULL,
    price_delta NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_order_item_modifiers_restaurant_id ON order_item_modifiers(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_order_item_modifiers_order_item_id ON order_item_modifiers(order_item_id);
