CREATE TABLE IF NOT EXISTS recipes (
    id UUID PRIMARY KEY,
    restaurant_link_id UUID NOT NULL REFERENCES restaurant_links(id) ON DELETE CASCADE,
    -- No FK: Recipe lives in a different database than Morá's Product (see docs/ARMAZEM_MORA.md).
    mora_product_id UUID NOT NULL,
    mora_product_name VARCHAR(150) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (restaurant_link_id, mora_product_id)
);

CREATE INDEX IF NOT EXISTS idx_recipes_restaurant_link_id ON recipes(restaurant_link_id);

CREATE TABLE IF NOT EXISTS recipe_items (
    id UUID PRIMARY KEY,
    recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id),
    quantity_per_unit NUMERIC(12,3) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_recipe_items_recipe_id ON recipe_items(recipe_id);

-- One row per restaurant link, keyed by its id directly (not a separate generated id) - holds
-- the "since" cursor for the next incremental sync call to Morá's sales endpoint.
CREATE TABLE IF NOT EXISTS sync_states (
    restaurant_link_id UUID PRIMARY KEY REFERENCES restaurant_links(id) ON DELETE CASCADE,
    last_synced_at TIMESTAMP WITH TIME ZONE NOT NULL
);
