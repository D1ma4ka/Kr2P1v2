-- Additive P5 -> P6. Existing requests, friendships and comments remain intact.
CREATE INDEX users_display_name_trgm ON users USING gin (catalog_normalize(display_name) gin_trgm_ops) WHERE status='active';
CREATE INDEX friend_requests_outbox ON friend_requests(from_user_id,created_at DESC,id DESC) WHERE status='pending';
CREATE INDEX checkins_feed ON checkins(created_at DESC,id DESC) WHERE deleted_at IS NULL AND hidden_at IS NULL;
-- A client-generated comment UUID is also its immutable retry key. Keep the original
-- request hash after deletion so retries cannot restore or overwrite a tombstone.
ALTER TABLE comments ADD COLUMN request_hash char(64);
