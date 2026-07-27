ALTER TABLE restaurants ADD COLUMN service_charge_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE restaurants ADD COLUMN service_charge_percentage NUMERIC(5, 2) NOT NULL DEFAULT 10.00;

ALTER TABLE tabs ADD COLUMN service_charge_percentage NUMERIC(5, 2);
ALTER TABLE tabs ADD COLUMN service_charge_amount NUMERIC(10, 2);
