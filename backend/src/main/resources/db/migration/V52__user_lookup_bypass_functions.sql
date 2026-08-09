-- Same narrow RLS bypass pattern as V49 (reservation-by-token), for a case the original RLS
-- design investigation (docs/RLS_DESIGN.md) missed: login authenticates by email BEFORE any
-- restaurant/tenant is known - that's the whole point of a login form - so
-- UserDetailsServiceImpl.loadUserByUsername (Spring Security's credential check itself),
-- AuthService.login and AuthService.forgotPassword all look users up by email with no
-- app.tenant_id set. email is globally unique across all restaurants (see V1 init_schema,
-- users.email UNIQUE), so this isn't a workaround for a design flaw - it mirrors the
-- reservation access_token case exactly: an intentionally global lookup key.
CREATE FUNCTION user_by_email(p_email text)
RETURNS SETOF users
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM users WHERE email = p_email;
$$;

REVOKE ALL ON FUNCTION user_by_email(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION user_by_email(text) TO app_runtime;

-- Same reasoning for the registration flow's duplicate-email check (AuthService.registerRestaurant):
-- it runs before the new restaurant even exists yet, so there's no tenant to scope to either.
CREATE FUNCTION user_email_exists(p_email text)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS(SELECT 1 FROM users WHERE email = p_email);
$$;

REVOKE ALL ON FUNCTION user_email_exists(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION user_email_exists(text) TO app_runtime;

-- Same reasoning again for AuthService.refreshToken: a RefreshToken is looked up by its own
-- opaque token (refresh_tokens carries no RLS - it's not restaurant_id-scoped), which gives a
-- lazy User proxy with its id already populated for free (no query yet - Hibernate fills a
-- ManyToOne proxy's id straight from the FK column). But actually loading that User row (to
-- read its restaurant/active flag) is a real `SELECT ... FROM users` that RLS would block with
-- no tenant known yet, same as the email cases above.
CREATE FUNCTION user_by_id(p_id uuid)
RETURNS SETOF users
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM users WHERE id = p_id;
$$;

REVOKE ALL ON FUNCTION user_by_id(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION user_by_id(uuid) TO app_runtime;
