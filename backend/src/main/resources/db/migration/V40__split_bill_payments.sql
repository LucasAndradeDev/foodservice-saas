CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    tab_id UUID NOT NULL REFERENCES tabs(id) ON DELETE CASCADE,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    payment_method VARCHAR(20) NOT NULL,
    amount NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    paid_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    voided_by UUID REFERENCES users(id) ON DELETE SET NULL,
    voided_at TIMESTAMP WITH TIME ZONE,
    void_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payments_tab_id ON payments(tab_id);

CREATE INDEX IF NOT EXISTS idx_payments_restaurant_paid_at_active
    ON payments(restaurant_id, paid_at) WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_payments_restaurant_cash_paid_at_active
    ON payments(restaurant_id, paid_at) WHERE status = 'ACTIVE' AND payment_method = 'CASH';

-- Backfill: each already-paid tab becomes one ACTIVE payment. The old cancel-payment flow overwrote
-- the payment fields in place, so there is no earlier-correction history left to reconstruct here —
-- this is a faithful copy of what the old schema still had, not an attempt to recover history that
-- never existed in the first place.
INSERT INTO payments (id, tab_id, restaurant_id, payment_method, amount, status, paid_at, created_at, updated_at)
SELECT gen_random_uuid(), t.id, t.restaurant_id, t.payment_method, t.paid_amount, 'ACTIVE', t.paid_at, t.paid_at, t.paid_at
FROM tabs t
WHERE t.paid_amount IS NOT NULL;

ALTER TABLE tabs ADD COLUMN bill_total NUMERIC(10,2);

-- Already-paid tabs: the old paid_amount only ever existed if it matched the total exactly (payTab
-- required an exact match), so paid_amount == the old total. Without this, tabs closed before this
-- migration would keep bill_total NULL forever and could never receive a correction through
-- registerPayments.
UPDATE tabs SET bill_total = paid_amount WHERE paid_amount IS NOT NULL;

ALTER TABLE tabs
    DROP COLUMN payment_method,
    DROP COLUMN paid_amount,
    DROP COLUMN paid_at,
    DROP COLUMN payment_cancelled_by,
    DROP COLUMN payment_cancelled_at,
    DROP COLUMN payment_cancel_reason;
