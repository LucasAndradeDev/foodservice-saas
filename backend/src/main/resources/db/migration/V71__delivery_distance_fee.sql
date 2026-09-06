-- Distance-based delivery fee (task 26.5, docs/DELIVERY.md "v2: geo real") - promoted to the
-- priority method, with the existing neighborhood/DeliveryZone table becoming the fallback used
-- when the restaurant hasn't set these up yet, or the customer's address doesn't geocode.

-- Restaurant's own fixed location, geocoded from its existing free-text `address` column
-- whenever that address is saved (RestaurantService#updateMyRestaurant). Null until a successful
-- geocode - distance mode is simply unavailable (falls back to zone) until then.
ALTER TABLE restaurants ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE restaurants ADD COLUMN longitude DOUBLE PRECISION;

-- Both null = distance mode not configured for this restaurant. Owner-set in the "Entrega"
-- settings tab, same partial-update convention as the rest of UpdateRestaurantRequest.
ALTER TABLE restaurants ADD COLUMN delivery_base_fee NUMERIC(10, 2);
ALTER TABLE restaurants ADD COLUMN delivery_fee_per_km NUMERIC(10, 2);

-- Recorded on the order for staff transparency only (DeliveryDetailsResponse) - the frozen
-- delivery_fee already stored is the one source of truth for what's charged, same as before.
ALTER TABLE delivery_details ADD COLUMN delivery_distance_km NUMERIC(6, 2);
ALTER TABLE delivery_details ADD COLUMN delivery_fee_method VARCHAR(20);
