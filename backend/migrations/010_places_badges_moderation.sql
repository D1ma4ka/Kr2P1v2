-- P7: additive metadata/rules only, never seed real businesses or user content.
ALTER TABLE venues ADD COLUMN version integer NOT NULL DEFAULT 1 CHECK(version>0);
ALTER TABLE venues ADD COLUMN coordinates_source text CHECK(coordinates_source IS NULL OR length(btrim(coordinates_source)) BETWEEN 1 AND 2000);
ALTER TABLE venues ADD COLUMN image_asset_id uuid REFERENCES media_assets(id);
ALTER TABLE venues ADD COLUMN image_source text;
ALTER TABLE venues ADD COLUMN is_test boolean NOT NULL DEFAULT false;
CREATE INDEX venues_name_search ON venues USING gin(catalog_normalize(name||' '||city||' '||coalesce(address,'')) gin_trgm_ops) WHERE status='verified';
CREATE INDEX venues_page ON venues(id) WHERE status='verified';
ALTER TABLE reports ADD COLUMN version integer NOT NULL DEFAULT 1 CHECK(version>0);
CREATE INDEX reports_reporter_recent ON reports(reporter_id,created_at DESC);
CREATE TABLE moderation_holds (
 subject_type text NOT NULL CHECK(subject_type IN ('user','checkin','comment','venue','position')),
 subject_id uuid NOT NULL, previous_state text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(subject_type,subject_id)
);

-- Versioned engineering rules documented in ADR-0010. No volume/time/streak goals.
INSERT INTO badge_rules(id,metric,target,title,description,icon_key,version) VALUES
 ('70000000-0000-4000-8000-000000000001','checkins',1,'Первая история','Сохраните один действующий чекин подтверждённой позиции.','glass',1),
 ('70000000-0000-4000-8000-000000000002','positions',3,'Исследователь','Сохраните впечатления о трёх разных подтверждённых позициях.','bottle',1),
 ('70000000-0000-4000-8000-000000000003','positions',10,'Коллекционер','В вашей истории десять разных подтверждённых позиций.','bottle',1),
 ('70000000-0000-4000-8000-000000000004','styles',3,'Три стиля','Познакомьтесь с тремя известными стилями. Неизвестные стили не считаются.','catalog',1),
 ('70000000-0000-4000-8000-000000000005','styles',5,'Палитра вкусов','Сохраните впечатления о пяти известных стилях.','catalog',1),
 ('70000000-0000-4000-8000-000000000006','breweries',3,'Знакомство с пивоварнями','В истории представлены три подтверждённые пивоварни.','home',1),
 ('70000000-0000-4000-8000-000000000007','breweries',5,'География пивоварен','В истории представлены пять подтверждённых пивоварен.','home',1),
 ('70000000-0000-4000-8000-000000000008','notes',3,'Автор заметок','Добавьте заметки к трём разным подтверждённым позициям.','feed',1),
 ('70000000-0000-4000-8000-000000000009','photos',3,'Фотоистории','Добавьте собственные доступные фото к трём разным позициям.','person',1),
 ('70000000-0000-4000-8000-000000000010','venues',2,'Знакомые места','Сохраните записи о двух разных подтверждённых заведениях.','people',1);

CREATE FUNCTION reconcile_badges(uid uuid) RETURNS void LANGUAGE plpgsql AS $$
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
  SELECT r.*,least(r.target,coalesce((m.facts->>r.metric)::int,0)) AS current FROM badge_rules r CROSS JOIN metrics m WHERE r.active
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
