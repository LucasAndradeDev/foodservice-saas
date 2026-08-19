-- Delivery feature (Prioridade 8, docs/DELIVERY.md), task 27.1: delivery status field.
-- Same shape as orders.status/tabs.status (V5/V6) - no CHECK constraint, validated by
-- Hibernate (@Enumerated(STRING)) and DeliveryService instead.
ALTER TABLE delivery_details ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SEPARATING';
