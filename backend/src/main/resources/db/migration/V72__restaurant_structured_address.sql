-- Structures the restaurant's own address (task 26.5 follow-up, docs/DELIVERY.md "v2: geo real").
-- `address` (free text) stays as-is for display/backfill compat - these new columns are additive,
-- same shape as delivery_details' customer address (V63), so RestaurantService can hand
-- GeocodingService a structured query (more accurate with Nominatim than a free-text search)
-- whenever they're filled in, falling back to `address` for restaurants that haven't re-entered
-- their address yet.
ALTER TABLE restaurants
    ADD COLUMN street VARCHAR(255),
    ADD COLUMN number VARCHAR(20),
    ADD COLUMN complement VARCHAR(255),
    ADD COLUMN neighborhood VARCHAR(100),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN zip_code VARCHAR(10);
