-- Live courier tracking on a map (task 28 follow-up, docs/DELIVERY.md originally deferred this
-- as "projeto à parte"). Only the latest known position is kept - no history/trail table, so
-- there's no location-retention question to answer. Nullable, only ever populated for a User
-- with role = COURIER, same convention as the phone/vehicle_type/notes columns added in V69.
ALTER TABLE users ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE users ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE users ADD COLUMN location_updated_at TIMESTAMP WITH TIME ZONE;
