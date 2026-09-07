-- Live ETA on the customer's tracking page (docs/DELIVERY.md follow-up to live courier tracking,
-- task 28). customer_latitude/longitude cache the geocoded delivery address from order creation
-- (DeliveryFeeResolver already geocodes it once to price the distance-based fee; reused here
-- instead of re-geocoding on every ETA refresh) - null whenever the order priced via DeliveryZone
-- instead (no geocoding happened) or the address didn't geocode, in which case ETA is simply never
-- shown. eta_minutes/eta_updated_at cache the last computed route duration so the courier's
-- position moving doesn't trigger a routing-provider call on every poll - refreshed at most once a
-- minute (DeliveryService#refreshEtaIfStale), not synchronously with the 4s status poll.
ALTER TABLE delivery_details ADD COLUMN customer_latitude DOUBLE PRECISION;
ALTER TABLE delivery_details ADD COLUMN customer_longitude DOUBLE PRECISION;
ALTER TABLE delivery_details ADD COLUMN eta_minutes INTEGER;
ALTER TABLE delivery_details ADD COLUMN eta_updated_at TIMESTAMP WITH TIME ZONE;
