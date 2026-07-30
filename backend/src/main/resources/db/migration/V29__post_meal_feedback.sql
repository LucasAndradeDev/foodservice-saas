CREATE TABLE IF NOT EXISTS post_meal_feedback (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    tab_id UUID NOT NULL REFERENCES tabs(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_post_meal_feedback_tab_id ON post_meal_feedback(tab_id);
CREATE INDEX IF NOT EXISTS idx_post_meal_feedback_restaurant_id ON post_meal_feedback(restaurant_id);
