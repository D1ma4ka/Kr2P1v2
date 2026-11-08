-- Additive catalog migration: no product/catalog seeds.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE FUNCTION catalog_normalize(value text) RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
 SELECT btrim(regexp_replace(replace(lower(normalize(coalesce(value,''),NFKC)),'ё','е'),'[^[:alnum:]]+',' ','g'))
$$;
ALTER TABLE beer_releases ADD COLUMN brand text CHECK(length(brand)<=200);
ALTER TABLE beer_positions ADD COLUMN image_asset_id uuid REFERENCES media_assets(id);
ALTER TABLE beer_positions ADD COLUMN image_source text;
ALTER TABLE breweries ADD COLUMN image_asset_id uuid REFERENCES media_assets(id);
ALTER TABLE breweries ADD COLUMN image_source text;
ALTER TABLE position_proposals ADD COLUMN version integer NOT NULL DEFAULT 1 CHECK(version>0);
ALTER TABLE position_proposals ADD COLUMN client_request_id uuid;
CREATE UNIQUE INDEX proposals_request ON position_proposals(submitter_id,client_request_id);
CREATE INDEX proposals_owner_page ON position_proposals(submitter_id,created_at DESC,id DESC);
CREATE TABLE proposal_revisions (
 proposal_id uuid NOT NULL REFERENCES position_proposals(id), version integer NOT NULL,
 proposed_data jsonb NOT NULL, changed_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(proposal_id,version)
);
CREATE TABLE barcode_exclusions (
 gtin text PRIMARY KEY CHECK(valid_gtin(gtin)), source text NOT NULL CHECK(length(btrim(source)) BETWEEN 1 AND 2000),
 moderator_id uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE catalog_search (
 position_id uuid PRIMARY KEY REFERENCES beer_positions(id), name_key text NOT NULL,
 search_text text NOT NULL, brewery_id uuid NOT NULL, style_id uuid,
 packaging text, abv numeric NOT NULL, created_at timestamptz NOT NULL
);
CREATE INDEX catalog_search_words ON catalog_search USING gin(search_text gin_trgm_ops);
CREATE INDEX catalog_search_newest ON catalog_search(created_at DESC,position_id DESC);
CREATE INDEX catalog_search_name ON catalog_search(name_key COLLATE "C",position_id);
CREATE INDEX catalog_search_abv ON catalog_search(abv,position_id);
CREATE INDEX catalog_search_brewery ON catalog_search(brewery_id,created_at DESC,position_id DESC);
CREATE INDEX catalog_search_style ON catalog_search(style_id,created_at DESC,position_id DESC);
CREATE INDEX breweries_name_key ON breweries(catalog_normalize(name),id);
CREATE INDEX releases_name_key ON beer_releases(brewery_id,catalog_normalize(name),id);
CREATE FUNCTION catalog_refresh(ids uuid[]) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 DELETE FROM catalog_search WHERE position_id=ANY(ids);
 INSERT INTO catalog_search(position_id,name_key,search_text,brewery_id,style_id,packaging,abv,created_at)
 SELECT p.id,catalog_normalize(r.name),catalog_normalize(r.name||' '||b.name||' '||coalesce(s.name,'')),
 r.brewery_id,r.style_id,p.packaging,coalesce(r.abv,-1),p.created_at
 FROM beer_positions p JOIN beer_releases r ON r.id=p.release_id JOIN breweries b ON b.id=r.brewery_id
 LEFT JOIN styles s ON s.id=r.style_id
 WHERE p.id=ANY(ids) AND p.status='verified' AND b.verification_status='verified';
END $$;
CREATE FUNCTION catalog_position_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM catalog_refresh(ARRAY[NEW.id]); RETURN NEW; END $$;
CREATE TRIGGER catalog_position_refresh AFTER INSERT OR UPDATE ON beer_positions FOR EACH ROW EXECUTE FUNCTION catalog_position_changed();
CREATE FUNCTION catalog_release_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM catalog_refresh(ARRAY(SELECT id FROM beer_positions WHERE release_id=NEW.id)); RETURN NEW; END $$;
CREATE TRIGGER catalog_release_refresh AFTER UPDATE ON beer_releases FOR EACH ROW EXECUTE FUNCTION catalog_release_changed();
CREATE FUNCTION catalog_brewery_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM catalog_refresh(ARRAY(SELECT p.id FROM beer_positions p JOIN beer_releases r ON r.id=p.release_id WHERE r.brewery_id=NEW.id)); RETURN NEW; END $$;
CREATE TRIGGER catalog_brewery_refresh AFTER UPDATE ON breweries FOR EACH ROW EXECUTE FUNCTION catalog_brewery_changed();
CREATE FUNCTION catalog_style_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM catalog_refresh(ARRAY(SELECT p.id FROM beer_positions p JOIN beer_releases r ON r.id=p.release_id WHERE r.style_id=NEW.id)); RETURN NEW; END $$;
CREATE TRIGGER catalog_style_refresh AFTER UPDATE ON styles FOR EACH ROW EXECUTE FUNCTION catalog_style_changed();
SELECT catalog_refresh(ARRAY(SELECT id FROM beer_positions WHERE status='verified'));
