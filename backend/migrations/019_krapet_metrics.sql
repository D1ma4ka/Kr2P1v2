ALTER TABLE kra_rooms ADD COLUMN last_visited_at timestamptz;
ALTER TABLE kra_partner_rewards ADD COLUMN min_published_checkins integer NOT NULL DEFAULT 0
 CHECK (min_published_checkins BETWEEN 0 AND 1000);
CREATE TABLE kra_metrics (
 day date NOT NULL,
 event text NOT NULL CHECK (event IN ('adopt','first_visit','return_visit','wardrobe','purchase','decor','partner_reward')),
 definition_id text NOT NULL REFERENCES kra_pet_definitions(id),
 count bigint NOT NULL CHECK (count > 0),
 PRIMARY KEY(day,event,definition_id)
);
INSERT INTO kra_animation_definitions(id,version,duration_ms,priority) VALUES
 ('idle',1,2600,0),
 ('greet',1,1050,2),
 ('blink',1,220,0),
 ('react',1,620,2),
 ('scratch',1,980,0),
 ('stretch',1,1040,0),
 ('look',1,1280,0),
 ('yawn',1,1060,0),
 ('alternate',1,540,2),
 ('return',1,1250,2),
 ('purchase',1,925,3),
 ('gift',1,1020,3),
 ('adjust_clothes',1,830,2),
 ('adjust_glasses',1,865,2),
 ('adjust_hat',1,920,2),
 ('inspect',1,1020,2),
 ('eat',1,3230,3),
 ('wipe',1,690,2),
 ('sit',1,840,2),
 ('appear',1,690,2),
 ('celebrate',1,1360,2);
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='krapiva_app') THEN
  EXECUTE 'REVOKE DELETE ON kra_metrics FROM krapiva_app';
  EXECUTE 'GRANT SELECT,INSERT,UPDATE ON kra_metrics TO krapiva_app';
 END IF;
END $$;
