-- Enables real Postgres RLS (Camada 3) on the 23 tables whose entities carry a direct
-- restaurant_id column and @Filter(name = "tenantFilter") (see docs/RLS_DESIGN.md). The
-- order_items case (no direct restaurant_id, filtered via a subquery through orders) is
-- handled separately in V51, mirroring the same split Camada 2 already uses.
--
-- NULLIF(..., '') before the ::uuid cast matters: TenantAwareDataSource writes '' (not SQL
-- NULL) to app.tenant_id for connections with no current tenant (e.g. the admin flow), and a
-- bare ''::uuid cast throws instead of failing closed. NULLIF turns that into SQL NULL first,
-- so restaurant_id = NULL correctly evaluates to false/no-rows instead of erroring.
--
-- Locally, `postgres` (the Flyway/backup owner role) is also the cluster superuser, so FORCE
-- ROW LEVEL SECURITY has no visible effect on it today - superusers always bypass RLS. It's
-- kept anyway because it becomes load-bearing the moment the owner role isn't a superuser
-- (e.g. a future production rollout on a managed Postgres where the connecting role isn't
-- superuser), and because the app never runs as the owner role for these tables regardless.
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'cash_register_sessions', 'categories', 'combo_items', 'combo_slots', 'combo_slot_options',
        'coupons', 'dining_areas', 'happy_hour_rules', 'monthly_goals', 'nav_notification_seen',
        'orders', 'order_item_modifiers', 'payments', 'post_meal_feedback', 'products',
        'product_availability_windows', 'product_modifier_groups', 'product_modifier_options',
        'reservations', 'restaurant_tables', 'tabs', 'table_requests', 'users'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I '
            || 'USING (restaurant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid) '
            || 'WITH CHECK (restaurant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)',
            t
        );
    END LOOP;
END
$$;
