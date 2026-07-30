CREATE TABLE IF NOT EXISTS product_availability_windows (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    day_of_week VARCHAR(10),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_product_availability_windows_restaurant_id ON product_availability_windows(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_product_availability_windows_product_id ON product_availability_windows(product_id);
