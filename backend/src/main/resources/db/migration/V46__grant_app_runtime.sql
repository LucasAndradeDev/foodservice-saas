-- Grants for app_runtime: every table Hibernate touches today, whether or not
-- it will end up with a Row-Level Security policy in a later migration.
-- Includes the two implicit @JoinTable tables (tab_tables, reservation_tables)
-- and cash_withdrawals, which have no restaurant_id column of their own and
-- are reached only through their already-scoped parent (tab/reservation/session).
GRANT SELECT, INSERT, UPDATE, DELETE ON
    -- entities with @Filter(name = "tenantFilter")
    cash_register_sessions, categories, combo_items, combo_slots, combo_slot_options,
    coupons, dining_areas, happy_hour_rules, monthly_goals, nav_notification_seen,
    orders, order_items, order_item_modifiers, payments, post_meal_feedback, products,
    product_availability_windows, product_modifier_groups, product_modifier_options,
    reservations, restaurant_tables, tabs, table_requests, users,
    -- JPA-managed but not tenant-filtered (tenant root, or scoped by token/user)
    restaurants, password_reset_tokens, email_verification_tokens, refresh_tokens,
    cash_withdrawals,
    -- implicit @JoinTable tables, no entity of their own
    tab_tables, reservation_tables
TO app_runtime;

-- So future migrations that add new tables don't silently forget to grant
-- access to app_runtime (they'd otherwise break the app the moment Hibernate
-- tries to touch a brand-new table on the restricted role).
-- CURRENT_USER, not a hardcoded role name: locally the owner/Flyway role is
-- literally called "postgres", but on Neon (production) it's "neondb_owner" -
-- hardcoding "postgres" here made this migration fail in production with
-- "role postgres does not exist".
ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO app_runtime;
