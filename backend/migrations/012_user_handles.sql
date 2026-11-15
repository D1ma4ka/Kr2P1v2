-- Existing UUIDs, hashes, sessions and relations are untouched. NULL means choose after login.
ALTER TABLE users ADD COLUMN handle text;
ALTER TABLE users ADD CONSTRAINT users_handle_format CHECK (
 handle IS NULL OR (handle ~ '^[a-z][a-z0-9_]{1,22}[a-z0-9]$' AND handle NOT LIKE '%\_\_%' AND handle=lower(handle))
);
CREATE UNIQUE INDEX users_handle_unique ON users(handle) WHERE handle IS NOT NULL;
