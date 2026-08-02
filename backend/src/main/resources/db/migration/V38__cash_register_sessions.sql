CREATE TABLE IF NOT EXISTS cash_register_sessions (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    opened_by UUID REFERENCES users(id) ON DELETE SET NULL,
    opening_amount NUMERIC(10,2) NOT NULL,
    status VARCHAR(10) NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    closed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    counted_amount NUMERIC(10,2),
    expected_amount NUMERIC(10,2),
    difference_amount NUMERIC(10,2),
    closing_notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_cash_register_sessions_restaurant_id ON cash_register_sessions(restaurant_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cash_register_sessions_open_per_restaurant
    ON cash_register_sessions (restaurant_id)
    WHERE status = 'OPEN';

CREATE TABLE IF NOT EXISTS cash_withdrawals (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES cash_register_sessions(id) ON DELETE CASCADE,
    amount NUMERIC(10,2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    withdrawn_by UUID REFERENCES users(id) ON DELETE SET NULL,
    withdrawn_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cash_withdrawals_session_id ON cash_withdrawals(session_id);
