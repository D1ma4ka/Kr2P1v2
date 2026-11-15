-- Additive P8.2: existing users, scrypt hashes and issued legacy tokens are preserved.
CREATE TABLE password_reset_codes (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    code_hash text NOT NULL,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
