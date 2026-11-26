ALTER TABLE beer_releases
 ADD COLUMN released_on date,
 ADD COLUMN release_date_source text,
 ADD CONSTRAINT verified_release_date_has_source CHECK (
  (released_on IS NULL AND release_date_source IS NULL) OR
  (released_on IS NOT NULL AND release_date_source IS NOT NULL AND length(btrim(release_date_source)) BETWEEN 1 AND 2000)
 );
