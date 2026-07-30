CREATE TABLE IF NOT EXISTS dining_areas (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dining_areas_restaurant_id ON dining_areas(restaurant_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dining_areas_restaurant_id_name ON dining_areas(restaurant_id, LOWER(name));

ALTER TABLE restaurant_tables ADD COLUMN IF NOT EXISTS area_id UUID REFERENCES dining_areas(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_restaurant_tables_area_id ON restaurant_tables(area_id);
