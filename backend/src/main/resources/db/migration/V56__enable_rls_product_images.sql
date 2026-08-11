-- product_images (V54) was added after the RLS rollout (V50) and was missed: it carries
-- restaurant_id and @Filter(name = "tenantFilter") on ProductImage.java like the other 23
-- direct-column tables in V50, but never got its RLS policy. Closing that gap here rather
-- than editing V50, since Flyway migrations already applied must stay immutable.
ALTER TABLE product_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_images FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON product_images
    USING (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (restaurant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
