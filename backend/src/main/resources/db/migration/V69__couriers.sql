-- Delivery feature (docs/DELIVERY.md), task 28: courier as a real logged-in staff account
-- (role COURIER on the existing users table) instead of a standalone entity - reuses the same
-- invite/login machinery every other staff role already has. The three columns below are only
-- ever populated when role = COURIER.
ALTER TABLE users ADD COLUMN phone VARCHAR(20);
ALTER TABLE users ADD COLUMN vehicle_type VARCHAR(20);
ALTER TABLE users ADD COLUMN notes VARCHAR(255);

-- SET NULL, not CASCADE: deactivating (or, in principle, deleting) a courier account must not
-- erase the delivery orders they carried - same reasoning as DeliveryZone not touching an
-- already-frozen delivery_fee on past orders.
ALTER TABLE delivery_details ADD COLUMN courier_id UUID REFERENCES users(id) ON DELETE SET NULL;
