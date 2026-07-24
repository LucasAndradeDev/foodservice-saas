ALTER TABLE restaurants ADD COLUMN slug VARCHAR(150);
CREATE UNIQUE INDEX uq_restaurants_slug ON restaurants(slug);

ALTER TABLE products ADD COLUMN image_url VARCHAR(500);
