-- Older clients keep writing country/city text and their original ten link slots.
ALTER TABLE user_profiles
 ADD COLUMN country_id text CHECK (country_id ~ '^[A-Z]{2}$'),
 ADD COLUMN city_id integer CHECK (city_id > 0),
 ADD CONSTRAINT profile_city_requires_country CHECK (city_id IS NULL OR country_id IS NOT NULL);

-- Four additional slots let typed links coexist with a full legacy profile.
ALTER TABLE profile_links
 DROP CONSTRAINT profile_links_slot_check,
 ADD CONSTRAINT profile_links_slot_check CHECK (slot BETWEEN 1 AND 14);
