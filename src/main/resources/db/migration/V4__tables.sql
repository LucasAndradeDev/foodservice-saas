CREATE TABLE IF NOT EXISTS restaurant_tables (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'FREE',
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_restaurant_tables_restaurant_id ON restaurant_tables(restaurant_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_restaurant_tables_restaurant_id_number ON restaurant_tables(restaurant_id, number);
