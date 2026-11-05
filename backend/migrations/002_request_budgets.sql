-- One durable budget per account, shared by all API instances and sessions.
CREATE TABLE request_budgets (
 user_id uuid PRIMARY KEY REFERENCES users(id),
 window_start timestamptz NOT NULL, count integer NOT NULL CHECK(count > 0)
);
