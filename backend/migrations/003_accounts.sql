-- Additive P3 migration; P2 sessions remain valid until their original expiry.
CREATE TABLE auth_budgets (
 key_hash char(64) PRIMARY KEY, window_start timestamptz NOT NULL, count integer NOT NULL
);
CREATE INDEX auth_budgets_expiry ON auth_budgets(window_start);
CREATE TABLE refresh_tokens (
 token_hash char(64) PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id),
 session_id uuid NOT NULL REFERENCES sessions(id), expires_at timestamptz NOT NULL,
 consumed_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user ON refresh_tokens(user_id);
CREATE TABLE account_deletion_requests (
 user_id uuid PRIMARY KEY REFERENCES users(id), requested_at timestamptz NOT NULL DEFAULT now(),
 erase_after timestamptz NOT NULL DEFAULT now()+interval '30 days', completed_at timestamptz
);
