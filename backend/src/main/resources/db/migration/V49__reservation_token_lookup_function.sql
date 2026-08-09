-- Narrow, deliberate RLS bypass for the one query that genuinely can't know the
-- restaurant up front: looking up a reservation by its opaque access token (the
-- public reservation-by-token link). SECURITY DEFINER runs as the function owner
-- (the table-owning role), so it sees all rows regardless of app.tenant_id -- but
-- only for this exact lookup, not a general grant on the reservations table.
CREATE FUNCTION reservation_by_access_token(p_token text)
RETURNS SETOF reservations
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM reservations WHERE access_token = p_token;
$$;

REVOKE ALL ON FUNCTION reservation_by_access_token(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION reservation_by_access_token(text) TO app_runtime;
