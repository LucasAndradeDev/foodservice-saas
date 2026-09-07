-- Optional cap on how far distance-priced delivery (V71) will quote/accept an order (finding #3
-- of the 2026-09-07 security review): without it, any address that geocodes successfully is
-- priced and accepted, even one in another city. Null = no cap, same opt-in shape as
-- delivery_base_fee/delivery_fee_per_km - existing restaurants are unaffected until the owner sets it.
ALTER TABLE restaurants
    ADD COLUMN max_delivery_distance_km NUMERIC(6, 2);
