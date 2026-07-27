ALTER TABLE restaurants ADD COLUMN kitchen_warning_threshold_minutes INTEGER NOT NULL DEFAULT 10;
ALTER TABLE restaurants ADD COLUMN kitchen_critical_threshold_minutes INTEGER NOT NULL DEFAULT 20;
