-- Transactional statement-level projection refresh avoids per-row joins during large imports.
DROP TRIGGER catalog_position_refresh ON beer_positions;
DROP TRIGGER catalog_release_refresh ON beer_releases;
DROP TRIGGER catalog_brewery_refresh ON breweries;
DROP TRIGGER catalog_style_refresh ON styles;
CREATE FUNCTION catalog_positions_changed_batch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM catalog_refresh(ARRAY(SELECT id FROM changed_positions)); RETURN NULL; END $$;
CREATE TRIGGER catalog_positions_insert AFTER INSERT ON beer_positions REFERENCING NEW TABLE AS changed_positions FOR EACH STATEMENT EXECUTE FUNCTION catalog_positions_changed_batch();
CREATE TRIGGER catalog_positions_update AFTER UPDATE ON beer_positions REFERENCING NEW TABLE AS changed_positions FOR EACH STATEMENT EXECUTE FUNCTION catalog_positions_changed_batch();
CREATE FUNCTION catalog_releases_changed_batch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM catalog_refresh(ARRAY(SELECT p.id FROM beer_positions p JOIN changed_releases r ON r.id=p.release_id)); RETURN NULL; END $$;
CREATE TRIGGER catalog_releases_update AFTER UPDATE ON beer_releases REFERENCING NEW TABLE AS changed_releases FOR EACH STATEMENT EXECUTE FUNCTION catalog_releases_changed_batch();
CREATE FUNCTION catalog_breweries_changed_batch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM catalog_refresh(ARRAY(SELECT p.id FROM beer_positions p JOIN beer_releases r ON r.id=p.release_id JOIN changed_breweries b ON b.id=r.brewery_id)); RETURN NULL; END $$;
CREATE TRIGGER catalog_breweries_update AFTER UPDATE ON breweries REFERENCING NEW TABLE AS changed_breweries FOR EACH STATEMENT EXECUTE FUNCTION catalog_breweries_changed_batch();
CREATE FUNCTION catalog_styles_changed_batch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM catalog_refresh(ARRAY(SELECT p.id FROM beer_positions p JOIN beer_releases r ON r.id=p.release_id JOIN changed_styles s ON s.id=r.style_id)); RETURN NULL; END $$;
CREATE TRIGGER catalog_styles_update AFTER UPDATE ON styles REFERENCING NEW TABLE AS changed_styles FOR EACH STATEMENT EXECUTE FUNCTION catalog_styles_changed_batch();
