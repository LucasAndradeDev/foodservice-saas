CREATE TABLE monthly_goals (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    month DATE NOT NULL,
    revenue_goal NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (restaurant_id, month)
);

CREATE INDEX idx_monthly_goals_restaurant_id ON monthly_goals(restaurant_id);
