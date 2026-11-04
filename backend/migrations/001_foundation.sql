-- No production seed data. All identifiers are shared across Android/iOS.
CREATE TABLE users (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 email text NOT NULL CHECK (email = lower(btrim(email)) AND length(email) BETWEEN 3 AND 254),
 display_name text NOT NULL CHECK (length(btrim(display_name)) BETWEEN 1 AND 80),
 password_hash text, email_verified_at timestamptz,
 adult_confirmed_at timestamptz NOT NULL,
 status text NOT NULL DEFAULT 'active' CHECK (status IN ('active','suspended','deleted')),
 role text NOT NULL DEFAULT 'user' CHECK (role IN ('user','moderator','admin')),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(email)
);
CREATE TABLE user_profiles (
 user_id uuid PRIMARY KEY REFERENCES users(id),
 first_name text CHECK (length(first_name) <= 80), last_name text CHECK (length(last_name) <= 80),
 country text CHECK (length(country) <= 100), city text CHECK (length(city) <= 120),
 bio text CHECK (length(bio) <= 2000), gender text CHECK (length(gender) <= 80),
 gender_visibility text NOT NULL DEFAULT 'private' CHECK (gender_visibility IN ('private','friends','public')),
 profile_visibility text NOT NULL DEFAULT 'private' CHECK (profile_visibility IN ('private','friends','public')),
 version integer NOT NULL DEFAULT 1 CHECK (version > 0), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE profile_links (
 user_id uuid NOT NULL REFERENCES users(id), slot smallint NOT NULL CHECK (slot BETWEEN 1 AND 10),
 url text NOT NULL CHECK (length(url) <= 2048 AND url ~ '^https://'),
 visibility text NOT NULL DEFAULT 'private' CHECK (visibility IN ('private','friends','public')),
 PRIMARY KEY(user_id,slot)
);
CREATE TABLE sessions (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id),
 token_hash char(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
 created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL,
 revoked_at timestamptz, CHECK(expires_at > created_at)
);
CREATE INDEX sessions_user ON sessions(user_id);
CREATE INDEX sessions_expiry ON sessions(expires_at) WHERE revoked_at IS NULL;
CREATE TABLE account_tokens (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id),
 purpose text NOT NULL CHECK (purpose IN ('verify_email','reset_password')),
 token_hash char(64) NOT NULL UNIQUE, created_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL, consumed_at timestamptz, CHECK(expires_at > created_at)
);
CREATE INDEX account_tokens_user ON account_tokens(user_id);
CREATE TABLE media_assets (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES users(id),
 storage_key text NOT NULL UNIQUE, thumbnail_key text NOT NULL UNIQUE,
 content_type text NOT NULL CHECK (content_type = 'image/jpeg'),
 size_bytes integer NOT NULL CHECK (size_bytes BETWEEN 1 AND 8388608),
 width integer NOT NULL CHECK (width BETWEEN 1 AND 4096), height integer NOT NULL CHECK (height BETWEEN 1 AND 4096),
 sha256 char(64) NOT NULL, moderation_status text NOT NULL DEFAULT 'pending'
 CHECK (moderation_status IN ('pending','approved','hidden')),
 created_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz,
 UNIQUE(id,owner_id)
);
CREATE INDEX media_owner ON media_assets(owner_id,created_at DESC,id DESC);
ALTER TABLE user_profiles ADD COLUMN avatar_asset_id uuid;
ALTER TABLE user_profiles ADD COLUMN cover_asset_id uuid;
ALTER TABLE user_profiles ADD FOREIGN KEY(avatar_asset_id,user_id) REFERENCES media_assets(id,owner_id);
ALTER TABLE user_profiles ADD FOREIGN KEY(cover_asset_id,user_id) REFERENCES media_assets(id,owner_id);
CREATE TABLE styles (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name text NOT NULL UNIQUE);
CREATE TABLE breweries (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name text NOT NULL CHECK(length(btrim(name)) BETWEEN 1 AND 200),
 country text, city text, description text, source text NOT NULL,
 verification_status text NOT NULL DEFAULT 'pending' CHECK(verification_status IN ('pending','verified','rejected')),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE beer_releases (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), brewery_id uuid NOT NULL REFERENCES breweries(id),
 name text NOT NULL CHECK(length(btrim(name)) BETWEEN 1 AND 200), style_id uuid REFERENCES styles(id),
 abv numeric(5,2) CHECK(abv BETWEEN 0 AND 100), ibu numeric(6,2) CHECK(ibu BETWEEN 0 AND 1000),
 description text, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX releases_brewery ON beer_releases(brewery_id,id);
CREATE INDEX releases_style ON beer_releases(style_id,id);
CREATE TABLE beer_positions (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), release_id uuid NOT NULL REFERENCES beer_releases(id),
 packaging text, volume_ml integer CHECK(volume_ml > 0), edition text, variant text,
 status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','verified','rejected','merged')),
 source text NOT NULL, verified_at timestamptz, merged_into_id uuid REFERENCES beer_positions(id),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK ((status = 'merged') = (merged_into_id IS NOT NULL)), CHECK(merged_into_id <> id)
);
CREATE INDEX positions_release ON beer_positions(release_id,id);
CREATE INDEX positions_page ON beer_positions(created_at DESC,id DESC) WHERE status = 'verified';
CREATE FUNCTION valid_gtin(value text) RETURNS boolean LANGUAGE sql IMMUTABLE STRICT AS $$
 SELECT CASE WHEN value ~ '^[0-9]{14}$' THEN
 (SELECT sum(substr(value,n,1)::int * CASE WHEN n % 2 = 1 THEN 3 ELSE 1 END) % 10 = 0
 FROM generate_series(1,14) AS n) ELSE false END
$$;
CREATE TABLE barcode_mappings (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), position_id uuid NOT NULL REFERENCES beer_positions(id),
 gtin text NOT NULL CHECK(valid_gtin(gtin)), source text NOT NULL,
 status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','verified','rejected')),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX barcodes_verified_gtin ON barcode_mappings(gtin) WHERE status = 'verified';
CREATE INDEX barcodes_position ON barcode_mappings(position_id);
CREATE TABLE position_proposals (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), submitter_id uuid NOT NULL REFERENCES users(id),
 proposed_data jsonb NOT NULL CHECK(jsonb_typeof(proposed_data) = 'object'),
 status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','accepted','rejected')),
 moderator_id uuid REFERENCES users(id), decision_reason text, position_id uuid REFERENCES beer_positions(id),
 created_at timestamptz NOT NULL DEFAULT now(), decided_at timestamptz
);
CREATE INDEX proposals_queue ON position_proposals(created_at,id) WHERE status = 'pending';
CREATE TABLE venue_types (code text PRIMARY KEY);
INSERT INTO venue_types(code) VALUES ('bar'),('beer_shop'); -- taxonomy, no invented venues
CREATE TABLE venues (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name text NOT NULL, type text NOT NULL REFERENCES venue_types(code),
 city text NOT NULL, country text, address text, source text NOT NULL,
 latitude double precision CHECK(latitude BETWEEN -90 AND 90),
 longitude double precision CHECK(longitude BETWEEN -180 AND 180),
 coordinates_verified_at timestamptz,
 status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','verified','hidden')),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK ((latitude IS NULL) = (longitude IS NULL)),
 CHECK ((latitude IS NULL) = (coordinates_verified_at IS NULL))
);
CREATE INDEX venues_city_type ON venues(city,type,id) WHERE status = 'verified';
CREATE INDEX venues_coordinates ON venues(latitude,longitude) WHERE status = 'verified' AND latitude IS NOT NULL;
CREATE TABLE checkins (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id),
 position_id uuid NOT NULL REFERENCES beer_positions(id),
 rating_quarters smallint CHECK(rating_quarters BETWEEN 1 AND 20),
 body text NOT NULL DEFAULT '' CHECK(length(body) <= 5000),
 occurred_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 visibility text NOT NULL DEFAULT 'private' CHECK(visibility IN ('private','friends','public')),
 venue_id uuid REFERENCES venues(id), venue_text text CHECK(length(venue_text) <= 300),
 serving_format text CHECK(length(serving_format) <= 80),
 version integer NOT NULL DEFAULT 1 CHECK(version > 0), deleted_at timestamptz, hidden_at timestamptz,
 UNIQUE(id,user_id), CHECK(venue_id IS NULL OR venue_text IS NULL)
);
CREATE INDEX checkins_diary ON checkins(user_id,created_at DESC,id DESC) WHERE deleted_at IS NULL AND hidden_at IS NULL;
CREATE INDEX checkins_position ON checkins(position_id,user_id) WHERE deleted_at IS NULL AND hidden_at IS NULL;
CREATE INDEX checkins_venue ON checkins(venue_id) WHERE venue_id IS NOT NULL;
CREATE TABLE checkin_media (
 checkin_id uuid NOT NULL, owner_id uuid NOT NULL, media_id uuid NOT NULL,
 sort_order smallint NOT NULL CHECK(sort_order BETWEEN 1 AND 4),
 PRIMARY KEY(checkin_id,media_id), UNIQUE(checkin_id,sort_order),
 FOREIGN KEY(checkin_id,owner_id) REFERENCES checkins(id,user_id),
 FOREIGN KEY(media_id,owner_id) REFERENCES media_assets(id,owner_id)
);
CREATE INDEX checkin_media_asset ON checkin_media(media_id);
CREATE TABLE taste_tags (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), label text NOT NULL UNIQUE);
CREATE TABLE checkin_tags (
 checkin_id uuid NOT NULL REFERENCES checkins(id), tag_id uuid NOT NULL REFERENCES taste_tags(id),
 PRIMARY KEY(checkin_id,tag_id)
);
CREATE TABLE wishlist_items (
 user_id uuid NOT NULL REFERENCES users(id), position_id uuid NOT NULL REFERENCES beer_positions(id),
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,position_id)
);
CREATE INDEX wishlist_page ON wishlist_items(user_id,created_at DESC,position_id DESC);
CREATE TABLE friend_requests (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), from_user_id uuid NOT NULL REFERENCES users(id),
 to_user_id uuid NOT NULL REFERENCES users(id), status text NOT NULL DEFAULT 'pending'
 CHECK(status IN ('pending','accepted','rejected','cancelled')),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK(from_user_id <> to_user_id)
);
CREATE UNIQUE INDEX friend_requests_pending_pair ON friend_requests(least(from_user_id,to_user_id),greatest(from_user_id,to_user_id)) WHERE status = 'pending';
CREATE INDEX friend_requests_inbox ON friend_requests(to_user_id,created_at DESC,id DESC) WHERE status = 'pending';
CREATE TABLE friendships (
 user_low uuid NOT NULL REFERENCES users(id), user_high uuid NOT NULL REFERENCES users(id),
 accepted_at timestamptz NOT NULL DEFAULT now(), CHECK(user_low < user_high), PRIMARY KEY(user_low,user_high)
);
CREATE INDEX friendships_reverse ON friendships(user_high,user_low);
CREATE TABLE reactions (
 actor_user_id uuid NOT NULL REFERENCES users(id), checkin_id uuid NOT NULL REFERENCES checkins(id),
 type text NOT NULL DEFAULT 'like' CHECK(type = 'like'), created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(actor_user_id,checkin_id,type)
);
CREATE INDEX reactions_checkin ON reactions(checkin_id);
CREATE TABLE comments (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), author_user_id uuid NOT NULL REFERENCES users(id),
 checkin_id uuid NOT NULL REFERENCES checkins(id), body text NOT NULL CHECK(length(btrim(body)) BETWEEN 1 AND 2000),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 deleted_at timestamptz, hidden_at timestamptz
);
CREATE INDEX comments_page ON comments(checkin_id,created_at,id) WHERE deleted_at IS NULL AND hidden_at IS NULL;
CREATE TABLE badge_rules (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), metric text NOT NULL, target integer NOT NULL CHECK(target > 0),
 title text NOT NULL, description text NOT NULL, icon_key text NOT NULL,
 version integer NOT NULL DEFAULT 1 CHECK(version > 0), active boolean NOT NULL DEFAULT true
);
CREATE TABLE user_badges (
 user_id uuid NOT NULL REFERENCES users(id), rule_id uuid NOT NULL REFERENCES badge_rules(id),
 progress integer NOT NULL DEFAULT 0 CHECK(progress >= 0), earned_at timestamptz,
 rule_version integer NOT NULL, updated_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,rule_id)
);
CREATE TABLE badge_pins (
 user_id uuid NOT NULL, rule_id uuid NOT NULL, slot smallint NOT NULL CHECK(slot BETWEEN 1 AND 3),
 PRIMARY KEY(user_id,rule_id), UNIQUE(user_id,slot), FOREIGN KEY(user_id,rule_id) REFERENCES user_badges(user_id,rule_id)
);
CREATE TABLE reports (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), reporter_id uuid NOT NULL REFERENCES users(id),
 subject_type text NOT NULL CHECK(subject_type IN ('user','checkin','comment','venue','position')),
 subject_id uuid NOT NULL, reason text NOT NULL CHECK(length(btrim(reason)) BETWEEN 1 AND 2000),
 status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','resolved','rejected')),
 created_at timestamptz NOT NULL DEFAULT now(), resolved_at timestamptz
);
CREATE INDEX reports_queue ON reports(created_at,id) WHERE status = 'pending';
CREATE TABLE audit_events (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), actor_id uuid REFERENCES users(id), action text NOT NULL,
 entity_type text NOT NULL, entity_id uuid NOT NULL, reason text,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_entity ON audit_events(entity_type,entity_id,created_at DESC);
CREATE TABLE idempotency_keys (
 user_id uuid NOT NULL REFERENCES users(id), key uuid NOT NULL, operation text NOT NULL,
 request_hash char(64) NOT NULL, resource_id uuid NOT NULL REFERENCES checkins(id),
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,key,operation)
);
