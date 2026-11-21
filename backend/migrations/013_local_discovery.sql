INSERT INTO venue_types(code) VALUES ('brewery');

CREATE TABLE discovery_events (
 id uuid PRIMARY KEY,
 venue_id uuid NOT NULL REFERENCES venues(id),
 title text NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 300),
 description text NOT NULL CHECK (length(btrim(description)) BETWEEN 1 AND 4000),
 source text NOT NULL CHECK (length(btrim(source)) BETWEEN 1 AND 2000),
 starts_at timestamptz NOT NULL,
 ends_at timestamptz NOT NULL CHECK (ends_at > starts_at),
 published boolean NOT NULL DEFAULT false,
 version integer NOT NULL DEFAULT 1 CHECK (version > 0),
 UNIQUE (id, venue_id)
);
CREATE INDEX discovery_events_venue ON discovery_events(venue_id, starts_at) WHERE published;

CREATE TABLE local_badge_targets (
 rule_id uuid PRIMARY KEY REFERENCES badge_rules(id),
 venue_id uuid NOT NULL REFERENCES venues(id),
 event_id uuid,
 starts_at timestamptz NOT NULL,
 ends_at timestamptz NOT NULL CHECK (ends_at > starts_at),
 FOREIGN KEY (event_id, venue_id) REFERENCES discovery_events(id, venue_id)
);
CREATE INDEX local_badge_targets_venue ON local_badge_targets(venue_id, starts_at);

CREATE TABLE local_badge_attendance (
 rule_id uuid NOT NULL REFERENCES local_badge_targets(rule_id),
 user_id uuid NOT NULL REFERENCES users(id),
 checkin_id uuid NOT NULL,
 verified_by uuid NOT NULL REFERENCES users(id),
 verified_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY (rule_id, user_id),
 FOREIGN KEY (checkin_id, user_id) REFERENCES checkins(id, user_id)
);
CREATE INDEX local_badge_attendance_owner ON local_badge_attendance(user_id, rule_id);

-- Eligibility is rechecked against the current moderation and check-in state.
CREATE VIEW eligible_local_badge_checkins AS
 SELECT t.rule_id, c.id AS checkin_id, c.user_id
 FROM local_badge_targets t
 JOIN venues v ON v.id=t.venue_id AND v.status='verified'
 JOIN checkins c ON c.venue_id=t.venue_id
 JOIN beer_positions bp ON bp.id=c.position_id AND bp.status='verified'
 JOIN beer_releases l ON l.id=bp.release_id
 JOIN breweries b ON b.id=l.brewery_id AND b.verification_status='verified'
 JOIN users u ON u.id=c.user_id AND u.status='active'
 LEFT JOIN discovery_events e ON e.id=t.event_id
 WHERE c.deleted_at IS NULL AND c.hidden_at IS NULL
   AND c.occurred_at BETWEEN t.starts_at AND t.ends_at
   AND (t.event_id IS NULL OR e.published AND c.occurred_at BETWEEN e.starts_at AND e.ends_at);

CREATE OR REPLACE FUNCTION reconcile_badges(uid uuid) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended(uid::text,220027));
 WITH mine AS (
  SELECT c.*,r.style_id,b.id AS brewery_id
  FROM checkins c JOIN beer_positions bp ON bp.id=c.position_id
  JOIN beer_releases r ON r.id=bp.release_id JOIN breweries b ON b.id=r.brewery_id
  JOIN users u ON u.id=c.user_id
  WHERE c.user_id=uid AND c.deleted_at IS NULL AND c.hidden_at IS NULL
   AND u.status='active' AND bp.status='verified' AND b.verification_status='verified'
 ), metrics AS (
  SELECT jsonb_build_object(
   'checkins',count(*),'positions',count(DISTINCT position_id),'styles',count(DISTINCT style_id),
   'breweries',count(DISTINCT brewery_id),
   'notes',count(DISTINCT position_id) FILTER(WHERE length(btrim(body))>0),
   'photos',count(DISTINCT position_id) FILTER(WHERE EXISTS(SELECT 1 FROM checkin_media cm JOIN media_assets ma ON ma.id=cm.media_id WHERE cm.checkin_id=mine.id AND ma.deleted_at IS NULL AND ma.moderation_status<>'hidden')),
   'venues',count(DISTINCT venue_id) FILTER(WHERE EXISTS(SELECT 1 FROM venues v WHERE v.id=venue_id AND v.status='verified'))
  ) AS facts FROM mine
 ), progress AS (
  SELECT r.*,CASE WHEN r.metric='local_attendance' THEN
    CASE WHEN EXISTS (
     SELECT 1 FROM local_badge_attendance a JOIN eligible_local_badge_checkins e
      ON e.rule_id=a.rule_id AND e.checkin_id=a.checkin_id AND e.user_id=a.user_id
     WHERE a.user_id=uid AND a.rule_id=r.id
    ) THEN 1 ELSE 0 END
   ELSE least(r.target,coalesce((m.facts->>r.metric)::int,0)) END AS current
  FROM badge_rules r CROSS JOIN metrics m WHERE r.active
 )
 INSERT INTO user_badges(user_id,rule_id,progress,earned_at,rule_version)
 SELECT uid,id,current,CASE WHEN current>=target THEN now() ELSE NULL END,version FROM progress
 ON CONFLICT(user_id,rule_id) DO UPDATE SET progress=excluded.progress,
  earned_at=CASE WHEN excluded.earned_at IS NULL THEN NULL ELSE coalesce(user_badges.earned_at,excluded.earned_at) END,
  rule_version=excluded.rule_version,updated_at=now()
 WHERE user_badges.progress IS DISTINCT FROM excluded.progress
  OR (user_badges.earned_at IS NULL) IS DISTINCT FROM (excluded.earned_at IS NULL)
  OR user_badges.rule_version IS DISTINCT FROM excluded.rule_version;
 DELETE FROM badge_pins p USING user_badges b,badge_rules r
 WHERE p.user_id=uid AND b.user_id=p.user_id AND b.rule_id=p.rule_id AND r.id=b.rule_id AND (b.earned_at IS NULL OR NOT r.active);
END $$;
