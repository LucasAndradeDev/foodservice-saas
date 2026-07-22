CREATE UNIQUE INDEX IF NOT EXISTS uq_products_restaurant_id_name ON products(restaurant_id, LOWER(name));
