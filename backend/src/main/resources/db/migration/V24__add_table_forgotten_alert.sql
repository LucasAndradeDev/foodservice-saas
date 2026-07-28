ALTER TABLE restaurants ADD COLUMN table_forgotten_warning_threshold_minutes INTEGER NOT NULL DEFAULT 30;
ALTER TABLE restaurants ADD COLUMN table_forgotten_critical_threshold_minutes INTEGER NOT NULL DEFAULT 60;
ALTER TABLE tabs ADD COLUMN last_order_at TIMESTAMP WITH TIME ZONE;
