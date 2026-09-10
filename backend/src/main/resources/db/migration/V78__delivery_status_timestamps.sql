-- Per-status timestamps for delivery_details, needed to show a courier elapsed time since
-- dispatch and a same-day delivery history (MyDeliveriesPage redesign). updated_at can't be
-- reused for either: it also moves on unrelated writes like the throttled ETA background
-- refresh (DeliveryEtaService), so it doesn't reliably mark the moment a status changed.
ALTER TABLE delivery_details
    ADD COLUMN out_for_delivery_at TIMESTAMPTZ,
    ADD COLUMN delivered_at TIMESTAMPTZ;
