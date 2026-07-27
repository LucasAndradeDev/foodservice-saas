ALTER TABLE tabs ADD COLUMN payment_cancelled_by VARCHAR(255);
ALTER TABLE tabs ADD COLUMN payment_cancelled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE tabs ADD COLUMN payment_cancel_reason VARCHAR(255);
