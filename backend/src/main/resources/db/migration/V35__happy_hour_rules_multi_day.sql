ALTER TABLE happy_hour_rules RENAME COLUMN day_of_week TO days_of_week;
ALTER TABLE happy_hour_rules ALTER COLUMN days_of_week TYPE VARCHAR(60);
