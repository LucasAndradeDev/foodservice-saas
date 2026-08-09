-- order_items has no restaurant_id column of its own (it's reached only through orders), so
-- its Camada 2 Hibernate filter already uses a subquery via orders instead of a direct column
-- comparison (see OrderItem.java). The RLS policy mirrors that same subquery condition, rather
-- than adding a redundant/inconsistent second scoping mechanism. Since orders itself carries
-- the tenant_isolation policy from V50, this subquery is transparently scoped a second time by
-- Postgres when it evaluates - harmless, not a conflict, same tenant condition either way.
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON order_items
    USING (order_id IN (
        SELECT id FROM orders WHERE restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ))
    WITH CHECK (order_id IN (
        SELECT id FROM orders WHERE restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    ));
