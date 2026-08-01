ALTER TABLE nav_notification_seen
    ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;

DELETE FROM nav_notification_seen;

ALTER TABLE nav_notification_seen
    ALTER COLUMN user_id SET NOT NULL;

DROP INDEX IF EXISTS uq_nav_notification_seen_restaurant_section;

CREATE UNIQUE INDEX IF NOT EXISTS uq_nav_notification_seen_restaurant_user_section
    ON nav_notification_seen(restaurant_id, user_id, section);
