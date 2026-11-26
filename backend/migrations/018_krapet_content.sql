CREATE TABLE kra_art (
 hash char(64) PRIMARY KEY CHECK (hash ~ '^[0-9a-f]{64}$'),
 bytes bytea NOT NULL CHECK (octet_length(bytes) BETWEEN 1 AND 1048576)
);
ALTER TABLE kra_item_definitions ADD COLUMN asset_hash char(64) REFERENCES kra_art(hash);
CREATE TABLE kra_animation_definitions (
 id text PRIMARY KEY CHECK (id ~ '^[a-z][a-z0-9_]{2,63}$'),
 version integer NOT NULL CHECK (version > 0),
 duration_ms integer NOT NULL CHECK (duration_ms BETWEEN 60 AND 12000),
 priority integer NOT NULL CHECK (priority BETWEEN 0 AND 9)
);
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='krapiva_app') THEN
  EXECUTE 'REVOKE INSERT,UPDATE,DELETE ON kra_art,kra_animation_definitions FROM krapiva_app';
  EXECUTE 'GRANT SELECT ON kra_art,kra_animation_definitions TO krapiva_app';
 END IF;
END $$;
