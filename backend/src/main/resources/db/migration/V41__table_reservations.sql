ALTER TABLE restaurant_tables ADD COLUMN capacity INTEGER NOT NULL DEFAULT 4;

ALTER TABLE restaurants ADD COLUMN reservation_block_before_minutes INTEGER NOT NULL DEFAULT 30;
ALTER TABLE restaurants ADD COLUMN reservation_block_after_minutes INTEGER NOT NULL DEFAULT 30;

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    customer_name VARCHAR(255) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    note VARCHAR(500),
    party_size INTEGER NOT NULL,
    reservation_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    access_token VARCHAR(36) NOT NULL UNIQUE,
    tab_id UUID REFERENCES tabs(id) ON DELETE SET NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reservations_restaurant_time ON reservations(restaurant_id, reservation_time);

CREATE TABLE reservation_tables (
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    table_id UUID NOT NULL REFERENCES restaurant_tables(id) ON DELETE CASCADE,
    PRIMARY KEY (reservation_id, table_id)
);
