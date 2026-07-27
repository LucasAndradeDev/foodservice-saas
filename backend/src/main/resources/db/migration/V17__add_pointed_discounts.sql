ALTER TABLE order_items ADD COLUMN discount_type VARCHAR(20);
ALTER TABLE order_items ADD COLUMN discount_value NUMERIC(10, 2);
ALTER TABLE order_items ADD COLUMN discount_reason VARCHAR(255);
ALTER TABLE order_items ADD COLUMN discount_applied_by VARCHAR(255);
ALTER TABLE order_items ADD COLUMN discount_applied_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE tabs ADD COLUMN discount_type VARCHAR(20);
ALTER TABLE tabs ADD COLUMN discount_value NUMERIC(10, 2);
ALTER TABLE tabs ADD COLUMN discount_reason VARCHAR(255);
ALTER TABLE tabs ADD COLUMN discount_applied_by VARCHAR(255);
ALTER TABLE tabs ADD COLUMN discount_applied_at TIMESTAMP WITH TIME ZONE;
