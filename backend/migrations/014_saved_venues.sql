CREATE TABLE user_saved_venues (
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 venue_id uuid NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
 saved_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY (user_id, venue_id)
);
CREATE INDEX user_saved_venues_by_venue ON user_saved_venues(venue_id);
