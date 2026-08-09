-- Runtime-only Postgres role for the application's Hibernate connections.
-- Not a table owner, no DDL rights: Flyway migrations and the pg_dump backup
-- keep using the owning role. This role is what Row-Level Security policies
-- (added in later migrations) will actually apply to, since a table owner
-- always bypasses RLS regardless of FORCE ROW LEVEL SECURITY.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_runtime') THEN
        CREATE ROLE app_runtime WITH LOGIN PASSWORD 'app_runtime_local_dev_only'
            NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
END
$$;
