-- Platform admin credentials, previously fixed env vars (ADMIN_USERNAME/ADMIN_PASSWORD_HASH),
-- now persisted so a forgot-password flow can actually update the password hash at runtime.
-- Exactly one row is expected to ever exist; enforced by application logic (AdminCredentialsSeeder),
-- not a DB constraint, since there's no natural single-row key to constrain on.
CREATE TABLE IF NOT EXISTS admin_credentials (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_password_reset_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
