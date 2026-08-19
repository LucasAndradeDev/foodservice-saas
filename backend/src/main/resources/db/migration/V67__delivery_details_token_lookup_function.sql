-- Same narrow RLS bypass as V49 (reservation_by_access_token), for the equivalent delivery
-- flow: the customer's public status page (and Pix/card charge creation) look up
-- delivery_details by its opaque access_token before the tenant is known.
CREATE FUNCTION delivery_details_by_access_token(p_token text)
RETURNS SETOF delivery_details
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM delivery_details WHERE access_token = p_token;
$$;

REVOKE ALL ON FUNCTION delivery_details_by_access_token(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION delivery_details_by_access_token(text) TO app_runtime;
