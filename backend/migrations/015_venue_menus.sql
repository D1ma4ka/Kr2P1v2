CREATE TABLE venue_menu_operators (
 venue_id uuid NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 role text NOT NULL CHECK (role IN ('owner','operator')),
 granted_by uuid REFERENCES users(id) ON DELETE SET NULL,
 PRIMARY KEY (venue_id,user_id)
);
CREATE INDEX venue_menu_operators_user ON venue_menu_operators(user_id);

CREATE TABLE venue_menus (
 venue_id uuid NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
 state text NOT NULL CHECK (state IN ('draft','published')),
 revision integer NOT NULL CHECK (revision > 0),
 source text NOT NULL CHECK (length(btrim(source)) BETWEEN 1 AND 500),
 updated_at timestamptz NOT NULL DEFAULT now(),
 updated_by uuid REFERENCES users(id) ON DELETE SET NULL,
 PRIMARY KEY (venue_id,state)
);
CREATE INDEX venue_menus_actor ON venue_menus(updated_by);

CREATE TABLE venue_menu_sections (
 venue_id uuid NOT NULL,
 state text NOT NULL,
 id uuid NOT NULL,
 name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120),
 ordinal integer NOT NULL CHECK (ordinal BETWEEN 0 AND 39),
 PRIMARY KEY (venue_id,state,id),
 UNIQUE (venue_id,state,ordinal),
 FOREIGN KEY (venue_id,state) REFERENCES venue_menus(venue_id,state) ON DELETE CASCADE
);

CREATE TABLE venue_menu_items (
 venue_id uuid NOT NULL,
 state text NOT NULL,
 section_id uuid NOT NULL,
 position_id uuid NOT NULL REFERENCES beer_positions(id),
 ordinal integer NOT NULL CHECK (ordinal BETWEEN 0 AND 249),
 price numeric(12,2) CHECK (price >= 0),
 currency text CHECK (currency ~ '^[A-Z]{3}$'),
 volume_ml integer CHECK (volume_ml BETWEEN 1 AND 100000),
 serving_format text CHECK (length(btrim(serving_format)) BETWEEN 1 AND 80),
 available boolean NOT NULL,
 CHECK ((price IS NULL) = (currency IS NULL)),
 PRIMARY KEY (venue_id,state,position_id),
 FOREIGN KEY (venue_id,state,section_id) REFERENCES venue_menu_sections(venue_id,state,id) ON DELETE CASCADE
);
CREATE INDEX venue_menu_items_position ON venue_menu_items(position_id);
CREATE INDEX venue_menu_items_section ON venue_menu_items(venue_id,state,section_id,ordinal);
