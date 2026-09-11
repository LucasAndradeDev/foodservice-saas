-- Manual admin approval for new restaurant signups: a restaurant is created via
-- /auth/register-restaurant with approved = false and can't log in until a platform admin
-- approves it from the admin panel. Existing restaurants are grandfathered in as approved
-- (the DEFAULT backfills every current row); only new signups get approved = false, set
-- explicitly by AuthService#registerRestaurant.
ALTER TABLE restaurants ADD COLUMN approved boolean NOT NULL DEFAULT true;

-- Same narrow RLS bypass pattern as V52 (user lookup functions): AdminRestaurantService needs
-- the newly-approved restaurant's owner email to notify them, but platform-admin requests never
-- set app.tenant_id (Restaurant itself carries no tenant @Filter, but `users` does), so a normal
-- query would come back empty under RLS.
CREATE FUNCTION owner_email_by_restaurant(p_restaurant_id uuid)
RETURNS text
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT email FROM users WHERE restaurant_id = p_restaurant_id AND role = 'OWNER' ORDER BY created_at ASC LIMIT 1;
$$;

REVOKE ALL ON FUNCTION owner_email_by_restaurant(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION owner_email_by_restaurant(uuid) TO app_runtime;
